package com.jun.servicemonitor;

import org.junit.Test;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import static org.junit.Assert.*;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class UnitTest {

    @Test
    public void testServiceMonitor() {
        try {
            ServiceMonitor monitor = new ServiceMonitor(3334, "test");
            assertTrue(monitor.isAlive());
            assertEquals("test", monitor.getName());
            monitor.interrupt();
            assertTrue(monitor.isInterrupted());
            // the closing connections message is printed
        } catch (IOException e) {
            System.err.print(e);
        }
    }

    @Test
    public void testServiceUsers() {
        try {
            int portNumber = 3333;
            ServiceMonitor monitor = new ServiceMonitor(portNumber, "test");
            Socket clientSocket;
            Socket clientSocket2;

            try {
                clientSocket = new Socket("localhost", portNumber);
                clientSocket2 = new Socket("localhost", portNumber);
                monitor.new ServiceUser(clientSocket);
            } catch (UnknownHostException e) {
                System.err.println(e);
            } catch (IOException e) {
                System.err.println(e);
            }
        } catch (IOException e) {
            System.err.print(e);
        }
    }
}