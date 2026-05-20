#!/bin/bash

# Pega o diretório onde o script está
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Carrega o .env do diretório do projeto
source "$DIR/.env"

# Mostra as variáveis para debug
echo "DB_USERNAME=$DB_USERNAME"
echo "DB_PASSWORD=$DB_PASSWORD"

# Roda o Spring Boot
mvn spring-boot:run