import java.io.IOException;
import java.util.concurrent.ScheduledFuture;

public class PutchunkTask implements Task {
    private final byte[] message;
    private final String file_id;
    private final int chunk_no;
    private final int replication_deg;
    private int current_attempt;
    private ScheduledFuture next_action;

    public PutchunkTask(String file_name, int chunk_no, int replication_deg, byte[] body) {
        this.file_id = MessageFactory.filenameEncode(file_name);
        this.chunk_no = chunk_no;
        this.replication_deg = replication_deg;
        this.current_attempt = 0;

        //TODO Discuss:
        // Currently the filename enconding is being duplicated - both in createPutchunkMessage and in the first line of this constructor - should this be changed?
        this.message = MessageFactory.createPutchunkMessage(file_name, chunk_no, replication_deg, body);

        // Kickstarting the communication "loop"
        ThreadManager.getInstance().executeLater(this::communicate);
    }

    @Override
    public void notify(CommonMessage msg) {
        if (msg.getMessageType() != ProtocolDefinitions.MessageType.STORED) {
            System.out.println("DBG: Message was not stored! Oops!");
            return;
        }

        System.out.println("Notified of STORED message!");

        //TODO implement this
    }

    @Override
    public void communicate() {
        if (this.current_attempt >= ProtocolDefinitions.MESSAGE_DELAYS.length) {
            System.out.printf("Maximum retries reached for PutchunkTask for fileid '%s' and chunk_no '%d'\n", this.file_id, this.chunk_no);
            TaskManager.getInstance().unregisterTask(this);
            return;
        }

        try {
            System.out.printf("Sending Putchunk message for fileid '%s' and chunk_no '%d' - attempt #%d\n", this.file_id, this.chunk_no, this.current_attempt + 1);
            ChannelManager.getInstance().getBackup().broadcast(this.message);
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.next_action = ThreadManager.getInstance().executeLater(() -> {
            //System.out.println("Did not get reply for the current delay, trying again");
            // System.out.printf("Last delay: %d\n", ProtocolDefinitions.MESSAGE_DELAYS[this.current_attempt]);
            this.current_attempt++;
            this.communicate();
        }, ProtocolDefinitions.MESSAGE_DELAYS[this.current_attempt]);
    }

    @Override
    public String toKey() {
        return ProtocolDefinitions.MessageType.STORED.name() + file_id + chunk_no;
    }
}
