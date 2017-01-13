package com.jun.servicemonitor;

import java.io.IOException;

/**
 * Created by Jun on 2016-11-18.
 */
public class Main {

    public static void main (String args[]) {
        // The default port number.
        int portNumber = 3847;
        if (args.length < 1) {
            System.out.println("Now using port number=" + portNumber);
        } else {
            portNumber = Integer.valueOf(args[0]).intValue();
        }

        try {
            ServiceMonitor t1 = new ServiceMonitor(portNumber, "mainT");
            //t1.interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
