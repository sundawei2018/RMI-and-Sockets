package edu.stevens.cs549.ftpserver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Enumeration;
import java.util.Stack;
import java.util.logging.Logger;

import edu.stevens.cs549.ftpinterface.IServer;

/**
 *
 * @author dduggan
 */
public class Server extends UnicastRemoteObject
        implements IServer {
	
	static final long serialVersionUID = 0L;
	
	public static Logger log = Logger.getLogger("edu.stevens.cs.cs549.ftpserver");
    
	/*
	 * For multi-homed hosts, must specify IP address on which to 
	 * bind a server socket for file transfers.  See the constructor
	 * for ServerSocket that allows an explicit IP address as one
	 * of its arguments.
	 */
	private InetAddress host;
	
	final static int backlog = 5;
	
	/*
	 *********************************************************************************************
	 * Current working directory.
	 */
    static final int MAX_PATH_LEN = 1024;
    private Stack<String> cwd = new Stack<String>();
    
    /*
     *********************************************************************************************
     * Data connection.
     */
    
    enum Mode { NONE, PASSIVE, ACTIVE };
    
    private Mode mode = Mode.NONE;
    
    /*
     * If passive mode, remember the server socket.
     */
    
    private ServerSocket dataChan = null;
    
    private InetSocketAddress makePassive () throws IOException {
	    	dataChan = new ServerSocket(0, backlog);
	    	mode = Mode.PASSIVE;
	    	return (InetSocketAddress)(dataChan.getLocalSocketAddress());
    }
    
    /*
     * If active mode, remember the client socket address.
     */
    private InetSocketAddress clientSocket = null;
    
    private void makeActive (InetSocketAddress s) {
    	clientSocket = s;
    	mode = Mode.ACTIVE;
    }
    
    /*
     **********************************************************************************************
     */
            
    /*
     * The server can be initialized to only provide subdirectories
     * of a directory specified at start-up.
     */
    private final String pathPrefix;

    public Server(InetAddress host, int port, String prefix) throws RemoteException {
	    	super(port);
	    	this.host = host;
	    	this.pathPrefix = prefix + "/";
	        log.info("A client has bound to a server instance.");
    }
    
    public Server(InetAddress host, int port) throws RemoteException {
        this(host, port, "/");
    }
    
    private boolean valid (String s) {
        // File names should not contain "/".
        return (s.indexOf('/')<0);
    }
    
    private static class GetThread implements Runnable {
	    	private ServerSocket dataChan = null;
	    	private FileInputStream fis = null;
	    	private FileOutputStream fos = null;
	    	private Socket xfer = null;
	    	public GetThread (ServerSocket s, FileInputStream f) { dataChan = s; fis = f; }

		public GetThread(Socket s, FileOutputStream f) { xfer = s; fos = f;}

		public void run () {
    		/*
    		 * TODO: Process a client request to transfer a file. Added by Dawei Sun
    		 */
    		try {
    			if (xfer == null) {
    				Socket clientSocket = dataChan.accept();
    				writeToSocket(clientSocket, fis);
    			}
    			else {
    				readFromSocket(xfer, fos);
    			}
    		}
    		catch (Exception e){
    			e.printStackTrace();
    		}
    	}

		private static void readFromSocket(Socket xfer, FileOutputStream os) {
			try {
				InputStream is = xfer.getInputStream();
				transfer(is, os);
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			
		}
    }
    
    public void get (String file) throws IOException, FileNotFoundException, RemoteException {
        if (!valid(file)) {
            throw new IOException("Bad file name: " + file);
        } 
        else if (mode == Mode.ACTIVE) {
        	/*
        	 * TODO: connect to client socket to transfer file. Added by Dawei Sun
        	 */
	        	Socket xfer = new Socket (clientSocket.getAddress(), clientSocket.getPort());
	        	FileInputStream in = new FileInputStream(path()+file);
	        	writeToSocket(xfer, in);

        	/*
			 * End TODO.
			 */
        } else if (mode == Mode.PASSIVE) {
            FileInputStream f = new FileInputStream(path()+file);
            new Thread (new GetThread(dataChan, f)).start();
        }
    }
    
    private static void writeToSocket(Socket xfer, FileInputStream is) {
    		try {
    			OutputStream os = xfer.getOutputStream();
    			transfer(is, os);
    		}
    		catch (Exception e){
    			e.printStackTrace();
    		}
    }
    
    private static void transfer(InputStream is, OutputStream os) {
    		try {
    			int cnt = 0;
    			byte[] bytes = new byte[512];
    			while ((cnt = is.read(bytes, 0, bytes.length)) > 0) {
    				os.write(bytes, 0, cnt);
    			}
    			os.flush();
    			os.close();
    			is.close();
    		}
    		catch (Exception e) {
    			e.printStackTrace();
    		}
    }
    
    public void put (String file) throws IOException, FileNotFoundException, RemoteException {
    	/*
    	 * TODO: Finish put (both ACTIVE and PASSIVE).
    
    	 */
    		try {
    			if (mode == Mode.ACTIVE) {
    				FileOutputStream fos = new FileOutputStream(path() + file);
    				Socket xfer = new Socket(clientSocket.getAddress(), clientSocket.getPort());
    				new Thread(new GetThread(xfer, fos)).start();
    			}
    			else if (mode == Mode.PASSIVE) {
    				FileOutputStream fos = new FileOutputStream(path() + file);
    				Socket xfer = dataChan.accept();
    				new Thread(new GetThread(xfer, fos)).start();
    			}
    		}
    		catch (Exception e){
    			e.printStackTrace();
    		}
    }
    
    public String[] dir () throws RemoteException {
        // List the contents of the current directory.
        return new File(path()).list();
    }

	public void cd(String dir) throws IOException, RemoteException {
		// Change current working directory (".." is parent directory)
		if (!valid(dir)) {
			throw new IOException("Bad file name: " + dir);
		} else {
			if ("..".equals(dir)) {
				if (cwd.size() > 0)
					cwd.pop();
				else
					throw new IOException("Already in root directory!");
			} else if (".".equals(dir)) {
				;
			} else {
				File f = new File(path());
				if (!f.exists())
					throw new IOException("Directory does not exist: " + dir);
				else if (!f.isDirectory())
					throw new IOException("Not a directory: " + dir);
				else
					cwd.push(dir);
			}
		}
	}

    public String pwd () throws RemoteException {
        // List the current working directory.
        String p = "/";
        for (Enumeration<String> e = cwd.elements(); e.hasMoreElements(); ) {
            p = p + e.nextElement() + "/";
        }
        return p;
    }
    
    private String path () throws RemoteException {
    		return pathPrefix+pwd();
    }
    
    public void port (InetSocketAddress s) {
    		makeActive(s);
    }
    
    public InetSocketAddress pasv () throws IOException {
    		return makePassive();
    }

}
