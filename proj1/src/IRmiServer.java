import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IRmiServer extends Remote {
    int registry(String owner, String plate_nr) throws RemoteException;
    String lookup(String plate_nr) throws RemoteException;
}
