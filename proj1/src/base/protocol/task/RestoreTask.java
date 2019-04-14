/*
package base.protocol.task;

import base.ProtocolDefinitions;
import base.channels.ChannelHandler;
import base.channels.ChannelManager;
import base.messages.CommonMessage;
import base.messages.InvalidMessageFormatException;
import base.messages.MessageFactory;
import base.messages.MessageWithChunkNo;
import base.protocol.task.extendable.Task;
import base.storage.Restorer;

public class RestoreTask extends Task {
    private final String file_name;
    protected int chunk_no;

    public RestoreTask(String file_id, String file_name) {
        super(file_id);
        this.chunk_no = 0;
        this.file_name = file_name;
        initRestorer();
        prepareMessage();
        startCommuncation();
    }

    protected synchronized int getChunkNo() {
        return chunk_no;
    }

    protected synchronized void incrementChunkNo() {
        this.chunk_no++;
    }

    private void initRestorer() {
        RestoreManager.getInstance().registerRestorer(new Restorer(this.file_name, this.file_id));
    }

    @Override
    public void notify(CommonMessage msg) {
        if (!this.isRunning()) {
            return;
        }

        if (msg.getMessageType() != ProtocolDefinitions.MessageType.CHUNK) {
            System.out.println("DBG:RestoreTask.notify::Message was not of type CHUNK!");
            return;
        }

        if (((MessageWithChunkNo) msg).getChunkNo() != this.getChunkNo() || !msg.getFileId().equals(this.file_id)) {
            System.out.println("DBG:RestoreTask.notify::Message target was not this specific task");
            return;
        }

        try {
            byte[] msg_body = msg.getBody();
            // Interrupt the next GETCHUNK messages
            this.pauseCommunication();
            Restorer r = RestoreManager.getInstance().getRestorer(() -> this.file_id);

            assert r != null;

            if (msg_body.length < ProtocolDefinitions.CHUNK_MAX_SIZE_BYTES) {
                // Last chunk, unregister this task and eventually stop the Restorer that is running
                this.unregister();
                r.stopWriter();
                r.addChunk(msg_body, this.getChunkNo());
            } else {
                r.addChunk(msg_body, this.getChunkNo());
                // Still have more chunks, increment chunk_no and reset number of retries.
                // Then, re-key the task (to receive the correct messages), re-generate the message and resume communication (will start from the original first delay)
                this.incrementChunkNo();
                this.resetAttemptNumber();
                TaskManager.getInstance().rekeyTask(this);
                this.prepareMessage();
                // Done explicitly since it is only relevant for this case (and this will be TODO refactored)
                this.resumeCommuncation();
                this.startCommuncation();
            }
        } catch (InvalidMessageFormatException ignored) {
        }
    }

    @Override
    protected byte[] createMessage() {
        return MessageFactory.createGetchunkMessage(file_id, this.getChunkNo());
    }

    @Override
    protected void handleMaxRetriesReached() {
        System.out.printf("Maximum retries reached for RestoreTask for fileid '%s', at chunk_no '%d'\n", this.file_id, this.getChunkNo());
        this.unregister();
        Restorer r = RestoreManager.getInstance().getRestorer(() -> this.file_id);
        assert r != null;
        r.haltWriter();
    }

    @Override
    protected void printSendingMessage() {
        System.out.printf("Sending GETCHUNK message for fileid '%s' and chunk_no '%d' - attempt #%d\n", this.file_id, this.getChunkNo(), this.getCurrentAttempt() + 1);
    }

    @Override
    public String toKey() {
        return ProtocolDefinitions.MessageType.CHUNK.name() + file_id + this.getChunkNo();
    }

    @Override
    protected ChannelHandler getChannel() {
        return ChannelManager.getInstance().getControl();
    }
}
*/
