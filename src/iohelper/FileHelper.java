package src.iohelper;

import java.io.*;

public class FileHelper {
    // private ObjectOutputStream out;
    // private ObjectInputStream in;

    // public Iohelper(ObjectOutputStream out, ObjectInputStream in){
    //     this.out = out;
    //     this.in = in;
    // }

    /**
     * Sends a file to the server.
     * 
     * @param path File path
     */
    public static void sendFile(String path,ObjectOutputStream out) {
        File f = new File(path);
        long fileSize = f.length();
        try {
            // Send file name
            out.writeObject(f.getName());
            // Send file size
            out.writeObject(fileSize);

            FileInputStream fin = new FileInputStream(f);
            InputStream input = new BufferedInputStream(fin);
            // Send file
            int bytesSent = 0;
            byte[] buffer = new byte[1024];
            while (fileSize > bytesSent) {
                int bytesRead = input.read(buffer, 0, 1024);
                bytesSent += bytesRead;
                out.write(buffer, 0, bytesRead);
                out.flush();
            }
            input.close();
            fin.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

  /**
     * Receive's a file from the server.
     * 
     * @param fileSize File size.
     * @param path File path
     */
    public static void receiveFile(Long fileSize, String path, ObjectInputStream in) {
        try {
            File f = new File(path);
            f.createNewFile();

            FileOutputStream fout = new FileOutputStream(f);
            OutputStream output = new BufferedOutputStream(fout);

            int bytesWritten = 0;
            byte[] buffer = new byte[1024];

            while (fileSize > bytesWritten) {
                int bytesRead = in.read(buffer, 0, 1024);
                output.write(buffer, 0, bytesRead);
                output.flush();
                fout.flush();
                bytesWritten += bytesRead;
                System.out.println(bytesWritten);
            }
            output.close();
            fout.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
}
