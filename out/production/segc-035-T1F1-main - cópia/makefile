DEVICE_SRC := IoTDevice \
MessageCode

SERVER_SRC := Device \
Domain \
IoTServer \
ServerThread \
ServerManager \
ServerResponse \
DomainStorage \
DeviceStorage \
UserStorage \
Utils

BIN_DIR := bin
DEVICE_DIR := src.client
SERVER_DIR := src.server

DEVICE_FULL_PATHS := $(addsuffix .java,$(addprefix $(DEVICE_DIR)/,$(DEVICE_SRC))) 
SERVER_FULL_PATHS := $(addsuffix .java,$(addprefix $(SERVER_DIR)/,$(SERVER_SRC))) 

all:
	javac -d bin $(DEVICE_FULL_PATHS) $(SERVER_FULL_PATHS)
	jar cvfe IoTDevice.jar src.client.IoTDevice -C ./bin $(DEVICE_DIR) \
-C ./bin src.others/FileHelper.class
	jar cvfe IoTServer.jar src.server.IoTServer -C ./bin $(SERVER_DIR) \
-C ./bin src.client/MessageCode.class -C ./bin src.others/FileHelper.class
	chmod +x ./attestation.sh
	./attestation.sh
clean:
	rm -r bin; mkdir bin
