package base.storage;

import base.ProtocolDefinitions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

public class StorageManager {
    private static StorageManager instance = new StorageManager();
    private String backup_dirname;
    private String restored_dirname;

    private int occupied_space_bytes = 0;
    private int max_space_kbytes = ProtocolDefinitions.INITIAL_STORAGE_MAX_KBS;

    public static StorageManager getInstance() {
        return instance;
    }

    private StorageManager() {
    }

    // TODO: Use
    /// Methods for managing occupied space

    private synchronized void updateOccupiedSpace(int diff_bytes) {
        this.occupied_space_bytes += diff_bytes;
    }

    public synchronized int getOccupiedSpaceBytes() {
        return this.occupied_space_bytes;
    }

    private synchronized boolean canStore(int data_length) {
        return this.occupied_space_bytes + data_length <= this.max_space_kbytes * ProtocolDefinitions.KB_TO_BYTE;
    }

    public synchronized void setMaxSpaceKbytes(int max_space_kbytes) {
        this.max_space_kbytes = max_space_kbytes;
    }

    public synchronized boolean storageOverCapacity() {
        return this.occupied_space_bytes > this.max_space_kbytes * ProtocolDefinitions.KB_TO_BYTE;
    }

    public void initStorage() {
        // Create the directories for this peer (see Moodle):
        // peerX/backup and peerX/restored
        final String peer_dirname = String.format("peer%s", ProtocolDefinitions.SERVER_ID);
        this.backup_dirname = peer_dirname + "/" + ProtocolDefinitions.BACKUP_DIRNAME + "/";
        this.restored_dirname = peer_dirname + "/" + ProtocolDefinitions.RESTORED_DIRNAME + "/";

        // Creating the actual directories:
        new File(this.backup_dirname).mkdirs();
        new File(this.restored_dirname).mkdirs();

        //TODO: Check already stored chunks and insert that data into ChunkBackupState (must also check the number of "storeds" somehow - read from file?)
    }

    public boolean storeChunk(String file_id, int chunk_no, byte[] data) {
        if (this.hasChunk(file_id, chunk_no)) {
            System.out.printf("\tChunk with file_id '%s' and chunk_no '%d' already stored", file_id, chunk_no);
            return true;
        }

        if (!this.canStore(data.length)) {
            System.out.printf("\tCannot store chunk with file_id '%s' and chunk_no '%d' - Storage would be over maximum!", file_id, chunk_no);
            return false;
        }

        System.out.printf("StorageManager.storeChunk::Storing chunk with file_id '%s' and chunk_no '%d'\n", file_id, chunk_no);

        // Ensuring that the parent directories exist so that the FileOutputStream can create the file correctly
        final String chunk_parent_dir = String.format("%s/%s/", this.backup_dirname, file_id);
        new File(chunk_parent_dir).mkdirs();

        final String chunk_path = String.format("%schk%d", chunk_parent_dir, chunk_no);

        try (FileOutputStream fos = new FileOutputStream(chunk_path)) {
            fos.write(data);
            //fos.close(); There is unnecessary since the instance of "fos" is created inside a try-with-resources statement, which will automatically close the FileOutputStream in case of failure
            // See https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html

            // Registration that a chunk was stored is done by the caller due to needing the replication degree, which is useless here

            // However, the number of bytes used is relevant so it is changed here
            this.updateOccupiedSpace(data.length);
            System.out.println("DBG: Occupied space is now " + this.getOccupiedSpaceBytes() + " bytes");
            return true;
        } catch (IOException e) {
            System.out.printf("StorageManager.storeChunk::Error in storing chunk with file_id '%s' and chunk_no '%d'\n", file_id, chunk_no);
            e.printStackTrace();
            return false;
        }
    }

    public boolean removeChunk(String file_id, int chunk_no) {
        //TODO
        /*
        if (!this.hasChunk(file_id, chunk_no)) {
            System.out.printf("Error deleting: Chunk with file_id '%s' and chunk_no '%d' is not stored", file_id, chunk_no);
            return false;
        }

        System.out.printf("StorageManager.removeChunk::Deleting chunk with file_id '%s' and chunk_no '%d'\n", file_id, chunk_no);

        // Ensuring that the parent directories exist so that the FileOutputStream can create the file correctly
        final String chunk_parent_dir = String.format("%s/%s/", this.backup_dirname, file_id);
        new File(chunk_parent_dir).mkdirs();

        final String chunk_path = String.format("%schk%d", chunk_parent_dir, chunk_no);

        try (FileOutputStream fos = new FileOutputStream(chunk_path)) {
            fos.write(data);
            //fos.close(); There is unnecessary since the instance of "fos" is created inside a try-with-resources statement, which will automatically close the FileOutputStream in case of failure
            // See https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html

            // Registration that a chunk was stored is done by the caller due to needing the replication degree, which is useless here

            // However, the number of bytes used is relevant so it is changed here
            this.updateOccupiedSpace(data.length);
            System.out.println("DBG: Occupied space is now " + this.getOccupiedSpaceBytes() + " bytes");
            return true;
        } catch (IOException e) {
            System.out.printf("StorageManager.storeChunk::Error in storing chunk with file_id '%s' and chunk_no '%d'\n", file_id, chunk_no);
            e.printStackTrace();
            return false;
        }
        */
    }

    /**
     * Used to ensure that a file exists and that it is empty before starting to append to it
     * @param file_name file to create or empty
     */
    public boolean createEmptyFile(String file_name) {
        final String file_path = String.format("%s/%s", this.restored_dirname, file_name);

        try (FileOutputStream ignored = new FileOutputStream(file_path)) {
            return true;
        } catch (IOException e) {
            System.out.printf("StorageManager.createEmptyFile::Error creating empty file for name '%s'", file_name);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * For use in restoring, for appending to the file that is currently being written to
     *
     * @param file_name name of file that is being restored
     */
    public boolean writeToFileEnd(String file_name, byte[] data) {
        final String file_path = String.format("%s/%s", this.restored_dirname, file_name);

        try (FileOutputStream fos = new FileOutputStream(file_path, true)) {
            fos.write(data, 0, data.length);
            return true;
        } catch (IOException e) {
            System.out.printf("StorageManager.writeToFileEnd::Error in appending to file '%s'\n", file_name);
            e.printStackTrace();
            return false;
        }
    }

    public byte[] getStoredChunk(String file_id, int chunk_no) {
        if (!this.hasChunk(file_id, chunk_no)) {
            // Does not have the chunk
            return null;
        }

        final String chunk_path = String.format("%s/%s/chk%d", this.backup_dirname, file_id, chunk_no);
        File f = new File(chunk_path);
        byte[] chunk_data = new byte[(int) f.length()];
        try (FileInputStream fis = new FileInputStream(chunk_path)) {
            fis.read(chunk_data);
            return chunk_data;
        } catch (IOException e) {
            System.out.printf("StorageManager.getStoredChunk::Error in reading chunk '%d' for file_id '%s'\n", chunk_no, file_id);
            e.printStackTrace();
            return null;
        }
    }

    private boolean hasChunk(String file_id, int chunk_no) {
        return ChunkBackupState.getInstance().isChunkBackedUp(file_id, chunk_no);
    }

    public void removeFileChunksIfStored(String file_id) {
        final String file_id_backup_path = String.format("%s/%s/", this.backup_dirname, file_id);
        File file_backup_dir = new File(file_id_backup_path);
        if (!file_backup_dir.exists()) {
            return;
        }

        int dbg_n_removed = 0;

        // If the directory exists, we have stored chunks - must delete them individually and then delete the directory itself
        // Must not forget to unregister from the ConcurrentHashMap
        // TODO: Maybe fix verboseness
        for (File chunk : Objects.requireNonNull(file_backup_dir.listFiles())) {
            // Space was freed so the used space is updated
            System.out.println("chunk.length() = " + chunk.length());
            this.updateOccupiedSpace(-1 * (int)chunk.length());
            // Unregistering as backed up chunks
            final int chunk_no = Integer.parseInt(chunk.getName().substring(3));
            ChunkBackupState.getInstance().unregisterBackup(file_id, chunk_no);
            System.out.println("DBG2: Occupied space is now " + this.getOccupiedSpaceBytes() + " bytes");
            dbg_n_removed++;

            // Deleting after due to needing the original size for updating the occupied space
            chunk.delete();
        }

        if (!file_backup_dir.delete()) {
            System.out.printf("Could not delete directory %s\n", file_backup_dir.getName());
        }

        System.out.printf("Removed %d files\n", dbg_n_removed);
    }

    public static byte[] readFromFile(String file_path) throws IOException {
        // See https://stackoverflow.com/questions/858980/file-to-byte-in-java

        File f = new File(file_path);
        long file_size = f.length();

        // Arrays in Java have to be created using int and not long
        // Thus, checking if there is no "size overflow"
        if (file_size > Integer.MAX_VALUE) {
            throw new IOException("File too large to read into a byte array!");
        }

        byte[] file_data = new byte[(int) file_size];

        try (FileInputStream is = new FileInputStream(f)) {
            int offset = 0, n_bytes_read;
            while (offset < file_data.length && (n_bytes_read = is.read(file_data, offset, file_data.length - offset)) >= 0) {
                offset += n_bytes_read;
            }

            // Ensuring that all of the bytes have been read
            if (offset < file_data.length) {
                throw new IOException("Could not read full contents of file " + file_path);
            }

            return file_data;
        }
    }
}
