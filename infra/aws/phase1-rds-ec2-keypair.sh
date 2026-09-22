#!/bin/bash
set -e

# La contrasena del usuario maestro de RDS NUNCA debe quedar escrita en este
# archivo. Se exige por variable de entorno antes de ejecutar el script:
#   export RDS_MASTER_PASSWORD='TuPasswordSegura123!'
#   ./phase1-rds-ec2-keypair.sh
if [ -z "$RDS_MASTER_PASSWORD" ]; then
  echo "ERROR: define la variable RDS_MASTER_PASSWORD antes de ejecutar este script." >&2
  echo "Ejemplo: export RDS_MASTER_PASSWORD='TuPasswordSegura123!'" >&2
  exit 1
fi

echo "== 1. Verificando identidad AWS =="
aws sts get-caller-identity

echo "== 2. Red: VPC y subnets =="
VPC_ID=$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text)
SUBNET_IDS=$(aws ec2 describe-subnets --filters Name=vpc-id,Values=$VPC_ID --query 'Subnets[].SubnetId' --output text)
echo "VPC_ID=$VPC_ID"
echo "SUBNET_IDS=$SUBNET_IDS"

echo "== 3. Security groups =="
MY_IP=$(curl -s ifconfig.me)/32
echo "MY_IP=$MY_IP"

EC2_SG_ID=$(aws ec2 create-security-group --group-name pedidos360-ec2-sg --description "Pedidos360 backend EC2" --vpc-id $VPC_ID --query 'GroupId' --output text)
aws ec2 authorize-security-group-ingress --group-id $EC2_SG_ID --protocol tcp --port 22 --cidr $MY_IP
aws ec2 authorize-security-group-ingress --group-id $EC2_SG_ID --protocol tcp --port 8080 --cidr 0.0.0.0/0
echo "EC2_SG_ID=$EC2_SG_ID"

RDS_SG_ID=$(aws ec2 create-security-group --group-name pedidos360-rds-sg --description "Pedidos360 RDS MySQL" --vpc-id $VPC_ID --query 'GroupId' --output text)
aws ec2 authorize-security-group-ingress --group-id $RDS_SG_ID --protocol tcp --port 3306 --source-group $EC2_SG_ID
echo "RDS_SG_ID=$RDS_SG_ID"

echo "== 4. RDS MySQL =="
aws rds create-db-subnet-group \
  --db-subnet-group-name pedidos360-subnet-group \
  --db-subnet-group-description "Pedidos360" \
  --subnet-ids $SUBNET_IDS

aws rds create-db-instance \
  --db-instance-identifier pedidos360-db \
  --db-instance-class db.t3.micro \
  --engine mysql \
  --engine-version 8.0 \
  --master-username admin \
  --master-user-password "$RDS_MASTER_PASSWORD" \
  --allocated-storage 20 \
  --vpc-security-group-ids $RDS_SG_ID \
  --db-subnet-group-name pedidos360-subnet-group \
  --no-publicly-accessible \
  --backup-retention-period 0

echo "== 5. Key pair para SSH =="
EXISTING_KEYS=$(aws ec2 describe-key-pairs --query 'KeyPairs[].KeyName' --output text)
echo "Keys existentes: $EXISTING_KEYS"
if [ -z "$EXISTING_KEYS" ]; then
  aws ec2 create-key-pair --key-name pedidos360-key --query 'KeyMaterial' --output text > pedidos360-key.pem
  chmod 400 pedidos360-key.pem
  KEY_NAME=pedidos360-key
  echo "Creado nuevo key pair: pedidos360-key (guardado en ./pedidos360-key.pem)"
else
  KEY_NAME=$(echo $EXISTING_KEYS | awk '{print $1}')
  echo "Usando key pair existente: $KEY_NAME (asegurate de tener su .pem descargado desde Cloud Labs)"
fi

echo "== Guardando variables para la Fase 2 en infra/aws/phase1-env.sh =="
cat > "$(dirname "$0")/phase1-env.sh" <<EOF
export VPC_ID=$VPC_ID
export SUBNET_IDS="$SUBNET_IDS"
export EC2_SG_ID=$EC2_SG_ID
export RDS_SG_ID=$RDS_SG_ID
export KEY_NAME=$KEY_NAME
EOF

echo ""
echo "== Esperando a que RDS este disponible (puede tardar 5-10 min) =="
aws rds wait db-instance-available --db-instance-identifier pedidos360-db
RDS_ENDPOINT=$(aws rds describe-db-instances --db-instance-identifier pedidos360-db --query 'DBInstances[0].Endpoint.Address' --output text)
echo "export RDS_ENDPOINT=$RDS_ENDPOINT" >> "$(dirname "$0")/phase1-env.sh"

echo ""
echo "===================================================="
echo "FASE 1 COMPLETA"
echo "RDS_ENDPOINT=$RDS_ENDPOINT"
echo "Variables guardadas en infra/aws/phase1-env.sh"
echo "===================================================="
