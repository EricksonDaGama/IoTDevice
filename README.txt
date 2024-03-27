
README - Projeto Cliente-Servidor IoT
Fase 1 do 1º trabalho de Segurança e Confiabilidade SegC Grupo 2
Authors:
Erickson Cacondo-53653, Pedro Barata 54483, Rodrigo Silva 54416

Este projeto consiste em um sistema cliente-servidor para dispositivos de Internet das Coisas (IoT).
Abaixo estão as instruções para compilar e executar o projeto.

Pré-Requisitos:
- Java Development Kit (JDK) - Recomendado JDK 11 ou superior.
- Um editor de texto ou IDE de sua preferência (ex.: Eclipse, IntelliJ IDEA).

Preparação Inicial:
1. Criação dos Arquivos Necessários:
   Antes de executar o projeto, crie os seguintes arquivos de texto na pasta root do projeto
   - users.txt: Para armazenar credenciais de usuários.
     Estrutura: Cada linha deve conter um usuário e sua senha, separados por dois-pontos. Exemplo:
       alan:123
       Luis:123
   - devices.txt: Para manter informações sobre dispositivos.
     Estrutura: Cada dispositivo é listado com seu nome seguido de informações. Exemplo:
       Dispositivo: alan:1
       Última Temperatura: 23°C
       Última Imagem: img1.jpg
       Dispositivo: luis:2
       Última Temperatura: 27°C
       Última Imagem: img2.jpg
   - dominios.txt: Para informações sobre domínios.
     Estrutura: Cada domínio deve ser listado seguido de seus detalhes. Exemplo:
       Domínio: Room01
       Owner: luis
       Usuários com permissão: luis, alan
       Dispositivos registrados: luis:2 
       Domínio: Room02
       Owner: alan
       Usuários com permissão: alan
       Dispositivos registrados: alan:2
   - executavel.txt: Para manter informações sobre executáveis.
     Estrutura: Nome do arquivo seguido do tamanho esperado. Exemplo:
       IoTDevice.jar:15000

2. Compilação dos Arquivos Java:
	1. Abrir diretório(pasta root do projeto ) onde estão os ficheiros jar
        2. Abrir um terminal e executar comando
Executando o Servidor:
1. No terminal ou prompt de comando, execute:
   java -jar IoTServer.jar
2. O servidor deve iniciar e aguardar conexões de clientes.

Executando o Cliente:
1. Abra um novo terminal ou janela de prompt de comando (não feche o terminal do servidor).
2. Correr o jar IoTDevice com <serverAddress> <dev-id> <user-id>
   java -jar IoTDevice.jar <serverAddress> <dev-id> <user-id>
3. O cliente se conectará ao servidor e você poderá seguir as instruções na tela.

# Limitações
+ Assume que o Servidor tem permissão para escrever no diretório em que está a ser executado.
+ Não inclui qualquer mecanismo de autenticação ou criptografia na transferência de ficheiros.

Notas Adicionais:
- Certifique-se de que as portas utilizadas (ex.: 23456) não estejam bloqueadas ou em uso por outro processo.
- O endereço do host (ex.: 'localhost') e a porta usada pelo cliente devem corresponder aos do servidor.
