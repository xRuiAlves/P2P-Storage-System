package base.protocol.task;

import base.ProtocolDefinitions;
import base.messages.CommonMessage;
import base.messages.MessageFactory;
import base.messages.MessageWithChunkNo;
import base.messages.MessageWithPasvPort;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashSet;

public class EnhancedPutchunkTask extends PutchunkTask {
    private final HashSet<String> ongoing_replications = new HashSet<>();

    public EnhancedPutchunkTask(String file_id, int chunk_no, int replication_deg, byte[] body) {
        super(file_id, chunk_no, replication_deg, body);
    }

    @Override
    protected void handleMaxRetriesReached() {
        this.unregister();
        System.out.printf("Maximum retries reached for EnhancedPutchunkTask for fileid '%s' and chunk_no '%d'\n", this.file_id, this.chunk_no);
    }

    @Override
    protected byte[] createMessage() {
        return MessageFactory.createPutchunkMessage(file_id, chunk_no, replication_deg, body, false);
    }

    @Override
    protected void printSendingMessage() {
        System.out.printf("Sending enhanced PUTCHUNK message for fileid '%s' and chunk_no '%d' - attempt #%d\n", this.file_id, this.chunk_no, this.current_attempt + 1);
    }

    @Override
    public String toKey() {
        return ProtocolDefinitions.MessageType.CANSTORE.name() + file_id + chunk_no;
    }

    public synchronized void notify(MessageWithPasvPort msg, InetAddress address) {
        if (msg.getMessageType() != ProtocolDefinitions.MessageType.CANSTORE) {
            System.out.println("DBG:PutchunkTask.notify::Message was not of type CANSTORE!");
            return;
        }

        if (msg.getChunkNo() != this.chunk_no || !msg.getFileId().equals(this.file_id)) {
            System.out.println("DBG:EnhancedPutchunkTask.notify::Message target was not this specific task");
            return;
        }

        // Ensuring that the observed replication degree is not larger than the desired one
        if (ongoing_replications.size() + replicators.size() >= this.replication_deg) {
            System.out.println("DBG:Outta here"); // TODO Remove
            return;
        }

        // Ensuring that backup is not attempted for the same Peer twice
        if (ongoing_replications.contains(msg.getSenderId()) || replicators.contains(msg.getSenderId())) {
            System.out.println("DBG:Outta here 2"); // TODO Remove
            return;
        }

        // Entering into protocol processing for a certain peer

        this.cancelCommunication();

        // Connecting to the remote peer
        try(Socket s = new Socket(address, msg.getPasvPort())) {
            ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
            oos.writeObject(this.body);
            ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
            boolean backup_success = (boolean) ois.readObject();
            if (backup_success) {
                this.replicators.add(msg.getSenderId());
                this.ongoing_replications.remove(msg.getSenderId());
                System.out.printf("yay!!: Registered %s as a replicator successfully\n#Replicators: %d\tReplication Degree: %d\n", msg.getSenderId(), this.replicators.size(), this.replication_deg);
                if (this.replicators.size() >= this.replication_deg) {
                    System.out.printf("Chunk '%d' for fileid '%s' successfully replicated with a factor of at least '%d'\n", this.chunk_no, this.file_id, this.replication_deg);
                    this.unregister();
                }
            } else {
                System.out.printf(":CCC : %s could not replicate the file!!\n#Replicators: %d\tReplication Degree: %d\n", msg.getSenderId(), this.replicators.size(), this.replication_deg);
                this.startCommuncation();
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            // Failure, resume communication
            this.startCommuncation();
        }
    }
}
