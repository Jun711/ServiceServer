package com.jun.servicemonitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by Jun on 2016-11-17.
 */
public class ServiceMonitor extends Thread {

    private final ServerSocket serverSocket;
    private final static int MAX_CLIENTS = 10;
    public static ServiceUser[] users = new ServiceUser[MAX_CLIENTS];
    private static Map<String, List<ServiceUser>> services;
    private static ScheduledExecutorService scheduler;
    private static List<ScheduledFuture<?>> schedules;

    public ServiceMonitor(int port, String threadName) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.services = new ConcurrentHashMap<String, List<ServiceUser>>();
        this.scheduler = Executors.newScheduledThreadPool(MAX_CLIENTS);
        this.schedules = new ArrayList<>();
        this.setName(threadName);
        start();
    }

    // Update the schedules when there is any change to service requests
    private void updateServices() {
        System.out.println("Updating Monitoring services");
        synchronized (this) {
            Iterator<ScheduledFuture<?>> scheduleIterator = schedules.iterator();
            while(scheduleIterator.hasNext()) {
                boolean res = scheduleIterator.next().cancel(true);
                System.out.println("Canceling all the schedules" + res);
            }
            Set<Map.Entry<String, List<ServiceUser>>> serviceSet = services.entrySet();
            Iterator<Map.Entry<String, List<ServiceUser>>> serviceSetIterator = serviceSet.iterator();

            while (serviceSetIterator.hasNext()) {
                Map.Entry entry = serviceSetIterator.next();
                String hostName = (String) entry.getKey();
                List<ServiceUser> serviceUsers = (List<ServiceUser>) entry.getValue();
                System.out.println("Number of Users: " +  serviceUsers.size());
                int freq;
                int port;
                // The else cases are to handle clients terminating halfway
                if ( serviceUsers.size() > 1 ) {
                    freq = 5;   // default to 5 second if there are multiple serviceUsers register for the same service
                                // It could be a minimum of all the frequencies set by all the users
                                // It is not specified
                } else if ( serviceUsers.size() == 1 ){
                    freq =  serviceUsers.get(0).frequency;
                } else {
                    freq = 0;
                }

                if ( serviceUsers.size() > 0) {
                    port =  serviceUsers.get(0).port;
                } else {
                    port = 0;
                }
                System.out.print("HostName: " + hostName + " port: " + port + " freq: " + freq + ".");

                ServiceMonitorWorker worker = new ServiceMonitorWorker(hostName, port, serviceUsers);
                ScheduledFuture<?> serviceHandle = scheduler.scheduleAtFixedRate(worker, 0, freq, TimeUnit.SECONDS);
                schedules.add(serviceHandle);
            }
        }
    }

    @Override
    public void run() {
        System.out.println("ServiceMonitor is running");
        while (!this.interrupted()) {
            System.out.println("Not interupted, waiting to accept new connections");
            try {
                // accepts new client connection and assign each of them to a new ServiceUser thread object
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted a new connection");
                int i;
                for ( i = 0; i < MAX_CLIENTS; i++ ) {
                    if (users[i] == null) {
                        (users[i] = new ServiceUser(clientSocket)).start();
                        System.out.println("Accepted a new client: " + i + " " + users[i]);
                        break;
                    }
                }
                if ( i == MAX_CLIENTS ) {
                    PrintStream os = new PrintStream(clientSocket.getOutputStream());
                    os.println("Server too busy. Try later.");
                    os.close();
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.out.println(e);
            }
        }

        try {
            this.serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        scheduler.shutdown();
        System.out.println("Close connections");
    }

    /*
     * A inner worker class to check if services are up by setting up tcp connections
     * scheduler's param requires a runnable class
     */
    class ServiceMonitorWorker implements Runnable {
        private String host;
        private int port;
        List<ServiceUser> users;

        public ServiceMonitorWorker(String host, int port, List<ServiceUser> users) {
            this.host = host;
            this.port = port;
            this.users = users;
        }

        @Override
        public void run() {
            System.out.println("ServiceMonitorWorker is working");
            boolean status = false;
            Socket socket = null;
            try {
                // sets up a tcp connection the service provider to check if the service is up
                socket = new Socket(host, port);
                System.out.println("Connected: " + socket);
                status = true;
                if (socket != null) {
                    socket.close();
                    System.out.println("Close socket");
                }
            } catch (Throwable e) {
                // It could catch different Exception to be more specific
                // in that case, I can pass along the error message too
                // catch (UnknownHostException e)
                // catch (IOException e)
                System.out.println("error");
                status = false;
            }

            Iterator<ServiceUser> userIterator = users.iterator();
            while(userIterator.hasNext()) {
                userIterator.next().setServiceStatus(status);
            }
        }
    }

    // An inner class to represent each service user/caller
    class ServiceUser extends Thread {
        private Socket clientSocket = null;
        private BufferedReader is = null;
        private PrintStream os = null;
        private boolean serviceStatus = false; // service is not connected at the beginning
        private String host = null;
        private int port;
        private int frequency; // in seconds

        public ServiceUser(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public void run() {
            try {
               //Create input and output streams for this service user / caller / client.
                try {
                    is = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    os = new PrintStream(clientSocket.getOutputStream());
                } catch (IOException e) {
                    System.err.println(e);
                }

                // Get the service's host, port and polling frequency from the user
                String hostName = "";
                int portNum = -1;
                int freq = -1;
                while (!this.interrupted()) {
                    // assuming users provide correct inputs
                    if (hostName == "") {
                        os.println("Please provide the host name.");
                        try {
                            hostName = is.readLine();
                        } catch (IOException e) {
                            System.err.println(e);
                            break;
                        }
                    } else if (portNum == -1) {
                        os.println("Please provide the portNum.");
                        try {
                            String input = is.readLine();
                            portNum = Integer.parseInt(input);
                        } catch (IOException e) {
                            System.err.println(e);
                            break;
                        }
                    } else if (freq == -1) {
                        os.println("Please provide the polling frequency in seconds.");
                        try {
                            String input = is.readLine();
                            freq = Integer.parseInt(input);
                        } catch (IOException e) {
                            System.err.println(e);
                            break;
                        }
                    } else {
                        break;
                    }
                }

                // Add the new service user / client to the services map
                synchronized (this) {
                    for (int i = 0; i < MAX_CLIENTS; i++) {
                        if (users[i] != null && users[i] == this) {
                            host = hostName;
                            port = portNum;
                            frequency = freq;
                            break;
                        }
                    }
                    if (services.containsKey(host)) {
                        List<ServiceUser> listOfUsers = services.get(host);
                        listOfUsers.add(this);
                        services.put(host, listOfUsers);
                        System.out.println("Add a user to an existing key");
                    } else {
                        List<ServiceUser> listOfUsers = new ArrayList<>();
                        listOfUsers.add(this);
                        services.put(host, listOfUsers);
                        System.out.println("Add a new key to the map");
                    }
                    System.out.println("Update is done to the map");
                    updateServices();
                }

                os.println("Service begins.");
                // Start serving the user. Users can change the host, port or polling frequency but is not implemented
                while (!this.interrupted()) {
                    String line = is.readLine();


                    if (line != null && line.startsWith("/host")) {
                        // update host
                        updateServices();
                    }
                    if (line != null && line.startsWith("/port")) {
                        // update port
                        updateServices();
                    }
                    if (line != null && line.startsWith("/freq")) {
                        // update freq
                        updateServices();
                    }
                    if (line != null && line.startsWith("/quit")) {
                        break;
                    }
                }
                os.println("*** Bye ***");

              /*
               * Clean up. Set the current thread variable to null so that a new client
               * could be accepted by the server.
               */
                synchronized (this) {
                    for (int i = 0; i < MAX_CLIENTS; i++) {
                        if (users[i] == this) {
                            users[i] = null;
                        }
                    }

                    List<ServiceUser> listOfUsers = services.get(host);
                    Iterator<ServiceUser> userIterator = listOfUsers.iterator();
                    while (userIterator.hasNext()) {
                        if (userIterator.next() == this) {
                            userIterator.remove();
                        }
                    }
                    services.put(host, listOfUsers);
                    updateServices();
                }

               // Close the output stream, close the input stream, close the socket.
                is.close();
                os.close();
                clientSocket.close();
            } catch (IOException e) {
                System.err.println(e);
            }
        }

        // Send status message to the service user/caller
        public void setServiceStatus (boolean status) {
            this.serviceStatus = status;
            if (os != null) {
                os.println("/status " + this.serviceStatus);
            }
        }

        // Get the service status
        public boolean getServiceStatus () {
            return this.serviceStatus;
        }
    }
}
