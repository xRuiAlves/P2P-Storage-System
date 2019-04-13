package base.protocol.subprotocols;

import base.protocol.task.EnhancedPutchunkTask;
import base.protocol.task.PutchunkTask;
import base.protocol.task.TaskManager;
import base.protocol.task.extendable.ITaskObserver;
import base.protocol.task.extendable.ObservableTask;
import base.protocol.task.extendable.Task;
import base.storage.requested.RequestedBackupFileChunk;
import base.storage.requested.RequestedBackupsState;

import java.util.concurrent.ConcurrentHashMap;

public class BackupSubprotocol implements ITaskObserver {
    private static final int MAX_RUNNING_PUTCHUNK_TASKS = 10;

    private final String file_id;
    private final int replication_degree;
    private final byte[][] chunks_data;
    private final ConcurrentHashMap<Integer, Task> running_tasks = new ConcurrentHashMap<>();
    private final int last_chunk_no;
    private final boolean is_enhanced_version;
    // Access to these fields must be synchronized
    private int last_running_chunk_no;
    private int n_backed_up;

    public BackupSubprotocol(String file_id, int replication_degree, byte[][] chunks_data, boolean is_enhanced_version) {
        this.file_id = file_id;
        this.replication_degree = replication_degree;
        this.chunks_data = chunks_data;
        this.last_chunk_no = chunks_data.length;
        this.is_enhanced_version = is_enhanced_version;
        this.last_running_chunk_no = 0;

        launchInitialTasks();
    }

    private synchronized int getLastRunningChunkNo() {
        return this.last_running_chunk_no;
    }

    private synchronized void incrementLastRunningChunkNo() {
        this.last_running_chunk_no++;
    }

    private synchronized void incrementNrBackedUpChunks() {
        this.n_backed_up++;
    }

    private synchronized int getNrBackedupChunks() {
        return this.n_backed_up;
    }

    private void stopAllTasks() {
        // TODO
        // Iterate over the hashmap keys and unregister all of the tasks. Print "not success"
        System.out.println("Stopping all of the tasks because one was not successful");
        this.running_tasks.values().forEach(Task::stopTask);
        this.running_tasks.clear();

        // Unregistering file from requested backups
        RequestedBackupsState.getInstance().unregisterRequestedFile(this.file_id);

        System.out.println("All tasks stopped.");
        System.out.printf("Backup of file with id %s unsuccessful. Running tasks terminated and process aborted.\n", this.file_id);

        // TODO Launch delete subprotocol for this file_id
    }

    @Override
    public void notifyEnd(boolean success, int task_id) {
        if (!success) {
            System.out.printf("Task for chunk %d was not successful.\n", task_id);
            this.stopAllTasks();
        } else {
            // Task was successful
            this.incrementNrBackedUpChunks();

            final int nr_backed_up_chunks = this.getNrBackedupChunks();
            displayProgressBar(nr_backed_up_chunks);

            if (nr_backed_up_chunks == this.last_chunk_no) {
                System.out.printf("-->File with id %s successfully backed up!!!\n", this.file_id);
                return;
            }
            // (This task is no longer being executed)
            this.running_tasks.remove(task_id);
            this.launchNextTask();
        }
    }

    protected void displayProgressBar(double nr_backed_up_chunks) {
        final int progress = (int) ((nr_backed_up_chunks / this.last_chunk_no) * 20);

        System.out.print("Progress: ");
        int i = 0;
        for (; i < progress; ++i) {
            System.out.print("=");
        }
        for (; i < 20; ++i) {
            System.out.print("_");
        }
        System.out.println();
    }

    private void launchInitialTasks() {
        while (this.running_tasks.size() < MAX_RUNNING_PUTCHUNK_TASKS && this.getLastRunningChunkNo() < this.last_chunk_no) {
            this.launchNextTask();
        }
    }

    private synchronized void launchNextTask() {
        if (this.running_tasks.size() >= MAX_RUNNING_PUTCHUNK_TASKS || this.getLastRunningChunkNo() >= this.last_chunk_no) {
            // Preventing launching more tasks than desired
            return;
        }

        final int last_running_chunk_no = this.getLastRunningChunkNo();

        System.out.printf("-->Launching task for chunk_no %03d. #Running tasks: %03d\n", last_running_chunk_no, this.running_tasks.size());

        ObservableTask ot;
        if (this.is_enhanced_version) {
            ot = new EnhancedPutchunkTask(file_id, last_running_chunk_no, replication_degree, chunks_data[last_running_chunk_no]);
        } else {
            ot = new PutchunkTask(file_id, last_running_chunk_no, replication_degree, chunks_data[last_running_chunk_no]);
        }
        ot.observe(this);
        TaskManager.getInstance().registerTask(ot);
        RequestedBackupsState.getInstance().getRequestedFileBackupInfo(file_id).registerChunk(new RequestedBackupFileChunk(file_id, last_running_chunk_no, replication_degree));
        this.running_tasks.put(last_running_chunk_no, ot);

        this.incrementLastRunningChunkNo();
    }
}
