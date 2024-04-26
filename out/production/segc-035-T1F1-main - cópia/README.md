# segc-035-T1F1
Fase 1 do 1º trabalho de Segurança e Confiabilidade

## Authors
- Bruna Santos - fc56328
- Yichen Cao - fc58165
- Filipe Costa - fc55549

## Compiling and building
Please use the Makefile:
```
$ make
```

### Attestation
The `makefile` is configured to run `attestation.sh` which creates `attestation.txt`. This text file contains the file size of `IoTDevice.jar`, computed in real-time.

## Executing
### Server
```
$ java -jar IoTServer.jar [port]
```
Args:
- `port` - TCP port used by the server. Default value is `12345`.

#### Storing of images on the server-side
Images that are sent to the server are stored in `./img/`.

### Client
```
$ java -jar IoTDevice.jar <serverAddress> <dev-id> <user-id>
```
Args:
- `<serverAddress>` - identifies the server with the format `<IP/hostname>[:Port]`. Default port value is `12345`.
- `<dev-id>` - `int` Device identifier
- `<user-id>` - `String` Local user identifier 

#### Sending/Receiving of Images and Temperatures
If you want to send an image `./image_name.jpg` to the server, you can execute the `EI ./image_name.jpg` command in the prompt.

Temperatures and images that are requested from the server via `RT` and `RI`,
respectively, are stored under `./temps_domainName.txt` `./Img_username_deviceID.jpg`.
