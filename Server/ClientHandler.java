package Server;

import java.io.*;
import java.net.*;
import model.Message;
import model.Protocol;

public class ClientHandler implements Runnable {

    private Socket socket;
    private UserManager userManager;
    private PrintWriter out;

    public ClientHandler(Socket socket, UserManager userManager) {
        this.socket = socket;
        this.userManager = userManager;
    }

  
    public void run() {
        try {
            BufferedReader in =
                    new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));

            out = new PrintWriter(socket.getOutputStream(), true);

            String msg;

            while((msg=in.readLine())!=null){
                userManager.broadcast(msg,this);
            }

        } catch(Exception e){
            System.out.println(e.getMessage());
        } finally{
            userManager.removeClient(this);
        }
    }

    public void sendMessage(String msg){
        if(out!=null){
            out.println(msg);
        }
    }
}
