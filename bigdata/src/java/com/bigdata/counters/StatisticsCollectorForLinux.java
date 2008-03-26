package com.bigdata.counters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Collection of host performance data using the <code>sysstat</code> suite.
 * 
 * @see http://pagesperso-orange.fr/sebastien.godard/
 * 
 * FIXME verify scaling for all collected counters.
 * 
 * @todo configuration parameters to locate the sysstat utilities (normally
 *       installed into /usr/bin).
 * 
 * <pre>
 *   vmstat
 *   procs -----------memory---------- ---swap-- -----io---- --system-- -----cpu------
 *    r  b   swpd   free   buff  cache   si   so    bi    bo   in   cs us sy id wa st
 *    0  0  19088 1099996 210388 2130812    0    0    26    17    0    0  5  2 92  1  0
 * </pre>
 * 
 * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan Thompson</a>
 * @version $Id$
 */
public class StatisticsCollectorForLinux extends AbstractStatisticsCollector {

    /**
     * Used to parse the timestamp associated with each row of the [pidstat]
     * output.
     */
    static protected final SimpleDateFormat f;
    static {

        f = new SimpleDateFormat("hh:mm:ss aa");

        /*
         * Code may be enabled for a runtime test of the date format.
         */
        if (true) {

            System.err.println("Format: " + f.format(new Date()));

            try {

                System.err.println("Parsed: " + f.parse("06:35:15 AM"));

            } catch (ParseException e) {

                log.error("Could not parse?");

            }

        }
        
    }
    
    /**
     * The process identifier for this process (the JVM).
     */
    static protected int pid;
    static {

        pid = getLinuxPIDWithBash();

    }
    
    /**
     * The Linux {@link KernelVersion}.
     */
    static protected StatisticsCollectorForLinux.KernelVersion kernelVersion;
    static {

        kernelVersion = KernelVersion.get();

    }

    /** reports on the host CPU utilization. */
    protected SarCpuUtilizationCollector sar1;

    /** reports on process performance counters (CPU, MEM, IO). */
    protected PIDStatCollector pidstat;

    public void start() {

        log.info("starting collectors");
        
        super.start();
        
        sar1.start();

        pidstat.start();

    }

    public void stop() {

        log.info("stopping collectors");
        
        super.stop();
        
        sar1.stop();

        pidstat.stop();

    }
    
    /**
     * Reports on the kernel version for a linux host.
     * 
     * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan
     *         Thompson</a>
     * @version $Id$
     * 
     * @todo there is more information lurking in the kernel version.
     * 
     * @see http://en.wikipedia.org/wiki/Linux_kernel for a summary of linux
     *      kernel versioning.
     */
    public static class KernelVersion {
        
        public final int version;
        public final int major;
        public final int minor;
        
        public KernelVersion(String val) {
            
            String[] x = val.split("[\\.]");
            
            assert x.length >= 2 : "Not a kernel version? "+val;
            
            version = Integer.parseInt(x[0]);
            major   = Integer.parseInt(x[1]);
            minor   = Integer.parseInt(x[2]);
            
        }
        
        /**
         * Return the version of the Linux kernel as reported by
         * <code>uname</code>
         * 
         * @return The {@link KernelVersion}
         * 
         * @throws RuntimeException
         *             if anything goes wrong.
         */
        static public StatisticsCollectorForLinux.KernelVersion get() {

            final List<String> commands = new LinkedList<String>();

            final Process pr;
            
            try {
            
                commands.add("/bin/uname");
                
                commands.add("-r");
                
                ProcessBuilder pb = new ProcessBuilder(commands);
                
                pr = pb.start();
                
                pr.waitFor();
            
            } catch (Exception ex) {
            
                throw new RuntimeException("Problem running command: ["
                        + commands + "]", ex);
                
            }
            
            if (pr.exitValue() == 0) {
            
                BufferedReader outReader = new BufferedReader(
                        new InputStreamReader(pr.getInputStream()));
                
                final String val;
                try {
                
                    val = outReader.readLine().trim();
                    
                } catch (IOException ex) {
                    
                    throw new RuntimeException(ex);
                    
                }
                
                log.info("read: [" + val + "]");

                return new KernelVersion(val);
                
            } else {
                
                throw new RuntimeException("Could not get PID: exitValue="
                        + pr.exitValue());
                
            }

        }

    }

    /**
     * Return the PID of the Java VM under Linux using bash.
     * 
     * @return The PID.
     * 
     * @throws RuntimeException
     *             if anything goes wrong.
     */
    static public int getLinuxPIDWithBash() {

        final List<String> commands = new LinkedList<String>();

        final Process pr;
        
        try {
        
            commands.add("/bin/bash");
            
            commands.add("-c");
            
            commands.add("echo $PPID");
            
            ProcessBuilder pb = new ProcessBuilder(commands);
            
            pr = pb.start();
            
            pr.waitFor();
        
        } catch (Exception ex) {
        
            throw new RuntimeException("Problem running command: ["
                    + commands + "]", ex);
            
        }
        
        if (pr.exitValue() == 0) {
        
            BufferedReader outReader = new BufferedReader(
                    new InputStreamReader(pr.getInputStream()));
            
            final String val;
            try {
            
                val = outReader.readLine().trim();
                
            } catch (IOException ex) {
                
                throw new RuntimeException(ex);
                
            }
            
            log.info("read: [" + val + "]");
            
            int pid = Integer.parseInt(val);
            
            return pid;
            
        } else {
            
            throw new RuntimeException("Could not get PID: exitValue="
                    + pr.exitValue());
            
        }

    }

    public StatisticsCollectorForLinux(int interval) {

        super(interval);

        // host wide collection
        sar1 = new SarCpuUtilizationCollector(interval,kernelVersion);

        // process specific collection.
        pidstat = new PIDStatCollector(pid, interval, kernelVersion);

    }

}
