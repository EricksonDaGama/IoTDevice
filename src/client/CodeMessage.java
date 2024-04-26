package src.client;

/**
 * Represents messages to be sent between server and client.
 */
public enum CodeMessage {
    WRONG_PWD("WRONG_PWD # Wrong password. Try again."),
    OK_NEW_USER("OK_NEW_USER # This user isn't registered."),
    OK_USER("OK_USER # User exists."),
    NOK_DEVID("NOK_DEVID # This device ID is already connected with this user. Try another <dev-id>."),
    OK_DEVID("OK_DEVID # User and device ok."),
    NOK_TESTED("NOK_TESTED # Server did not validate."),
    OK_TESTED("OK_TESTED # Server validated."),
    OK("OK"),
    NOK("NOK"),
    NODM("NODM # Domain does not exist."),
    NOPERM("NOPERM # User doesn't have read permission."),
    NODATA("NODATA # Device id has no published data."),
    NOUSER("NOUSER # User does not exist."),
    NOID("NOID # Device id does not exist."),
    USEREXISTS("USEREXISTS # User already exists in domain."),  
    DEVICEEXISTS("DEVICEEXISTS # Device already exists in domain."),  
    AU("AU # Authenticate the User user:pwd pair"),
    AD("AD # Authenticate the Device"),
    TD("TD # Test the Device"),
    CREATE("CREATE <dm> # Create domain, user is owner"),
    ADD("ADD <user1> <dm> # add <user1> to <dm>"),
    RD("Register the current Device in the <dm>"),
    ET("ET <float> # Enviar Temperature <float> to the server"),
    EI("EI <filename.jpg> # Enviar Image <filename.jpg> to the server"),
    RT("RT <dm> # Receive the latest Temperatures for every device of domain <dm>, as long as user has permission"),
    RI("RI <user-id>:<dev-id> # Receive Image file from <user-id>:<dev-id>, as long as user has permission."),
    STOP("STOP # Client has been shut down."),
    EMAIL_FAIL("EMAIL_FAIL # Email sending API failed."), 
    WRONG_NONCE("WRONG_NONCE # ");



    private String desc;

    CodeMessage(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

}
