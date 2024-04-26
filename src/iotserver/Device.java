package src.iotserver;

import java.util.HashSet;
import java.util.Set;


    public class Device {
        private String userId;
        private String devId;
        private String fullId;

        private boolean online;
        private String imgPath;
        private Float temp;
        private Set<String> registeredDomains;

        public Device(String fullId) {
            this(fullId.split(":")[0], fullId.split(":")[1]);
        }

        public Device(String userId, String devId) {
            this.userId = userId;
            this.devId = devId;
            this.fullId = String.join(":", userId, devId);
            this.online = false;
            this.imgPath = null;
            this.temp = null;
            this.registeredDomains = new HashSet<>();
        }

        public boolean isOnline() {
            return online;
        }

        public void goOnline() {
            online = true;
        }

        public void goOffline() {
            online = false;
        }

        public String fullId() {
            return fullId;
        }

        public void registerImage(String imgPath) {
            this.imgPath = imgPath;
        }

        public void registerInDomain(String domainName) {
            registeredDomains.add(domainName);
        }

        public void registerTemperature(float temperature) {
            temp = temperature;
        }

        public Float getTemperature() {
            return temp;
        }

        public String getImagePath() {
            return imgPath;
        }

        public Set<String> getDomains() {
            return new HashSet<>(registeredDomains);
        }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Device)) return false;

        Device other = (Device) obj;
        return (userId != null ? userId.equals(other.userId) : other.userId == null) &&
                (devId != null ? devId.equals(other.devId) : other.devId == null);
    }



    @Override
    public int hashCode() {
        return 31 * this.userId.hashCode() + this.devId.hashCode();
    }


    @Override
    public String toString() {
        return formatOutput();
    }

    private String formatOutput() {
        final char NL = '\n';
        final char SP = ':';

        String temperature = (getTemperature() != null) ? Float.toString(getTemperature()) : "";
        String imagePath = (getImagePath() != null) ? getImagePath() : "";

        StringBuilder sb = new StringBuilder();
        sb.append(fullId()).append(SP)
                .append(temperature).append(SP)
                .append(imagePath).append(NL);

        return sb.toString();
    }


}
