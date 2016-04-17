package org.nustaq.kluster.processes;

import com.beust.jcommander.JCommander;
import org.nustaq.kontraktor.Actor;
import org.nustaq.kontraktor.Actors;
import org.nustaq.kontraktor.IPromise;
import org.nustaq.kontraktor.Promise;
import org.nustaq.kontraktor.remoting.base.ConnectableActor;
import org.nustaq.kontraktor.remoting.tcp.TCPConnectable;
import org.nustaq.kontraktor.remoting.tcp.TCPNIOPublisher;
import org.nustaq.kontraktor.util.Log;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Created by ruedi on 16/04/16.
 */
public class ProcessStarter extends Actor<ProcessStarter> {

    HashMap<String,StarterDesc> siblings;
    ConnectableActor primarySibling;
    StarterDesc primaryDesc;
    String id;
    String name;
    Map<String,ProcessInfo> processes = new HashMap<>();
    int pids = 1;

    public void init( StarterArgs options ) {
        siblings = new HashMap<>();
        id = UUID.randomUUID().toString();
        if ( options.getName() == null ) {
            try {
                this.name = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                Log.Error(this,e);
                this.name = "unknown";
            }
        } else {
            name = options.getName();
        }
        if ( options.getSiblingHost() != null ) {
            primarySibling = new TCPConnectable(ProcessStarter.class, options.getSiblingHost(),options.getSiblingPort());
            ProcessStarter sibling =
                (ProcessStarter) primarySibling
                    .connect(
                        (x, y) -> System.out.println("client disc " + x),
                        act -> {
                            System.out.println("act " + act);
                            siblings.entrySet().forEach(en -> self().execute(() -> siblings.remove(en.getKey())));
                        }
                    ).await();
            primaryDesc = sibling.getInstanceDesc().await();
            siblings.put(primaryDesc.getId(),primaryDesc);
            discoverSiblings(primaryDesc);
        }
    }

    public void cycle() {
        if ( ! isStopped() ) {
            discoverSiblings(primaryDesc);
            delayed(60_000, () -> cycle() );
        }
    }

    public IPromise<Map<String,StarterDesc>> register(StarterDesc self) {
        if ( ! siblings.containsKey(self.getId()) )
            siblings.put(self.getId(),self);
        return resolve(siblings);
    }

    private void discoverSiblings(StarterDesc desc) {
        if ( desc.getId().equals(id) )
            return;
        if (desc.getRemoteRef().isStopped()) {
            execute( () -> siblings.remove(desc.getId()) );
        } else {
            desc.getRemoteRef().register(getDesc()).then((rsib, err) -> {
                rsib.forEach((rid, rdesc) -> {
                    if (!siblings.containsKey(id)) {
                        siblings.put(rid,rdesc);
                        discoverSiblings(rdesc);
                    }
                });
            });
        }
    }

    public IPromise terminateProcess( String id, boolean force, int timeoutSec ) {
        ProcessInfo processInfo = processes.get(id);
        if ( processInfo == null )
            return resolve(null);
        if ( force )
            processInfo.getProc().destroyForcibly();
        else
            processInfo.getProc().destroy();
        Promise res = new Promise();
        exec( () -> {
            processInfo.getProc().waitFor( timeoutSec, TimeUnit.SECONDS);
            if ( processInfo.getProc().isAlive() ) {
                res.reject("timeout");
            } else {
                processes.remove(processInfo.getId());
                res.resolve(processInfo);
            }
            return null;
        });
        return res;
    }

    public IPromise<ProcessInfo> startProcess( String workingDir, Map<String,String> env, String ... commandLine ) {
        ProcessBuilder pc = new ProcessBuilder(commandLine);
        if ( env != null ) {
            pc.environment().putAll(env);
        }

        pc.directory(new File(workingDir));
        try {
            Process proc = pc.start();
            ProcessInfo pi = new ProcessInfo().cmdLine(commandLine).id(this.id + ":" + pids++).proc(proc);
            processes.put(pi.getId(), pi);
            return resolve(pi);
        } catch (IOException e) {
            Log.Warn(this, e);
            return reject(e);
        }
    }

    public IPromise<StarterDesc> getInstanceDesc() {
        return resolve(getDesc());
    }

    private StarterDesc getDesc() {
        return new StarterDesc().host(name).id(id).remoteRef(self());
    }

    public IPromise<List<ProcessInfo>> getProcesses() {
        return resolve(processes.entrySet().stream().map( x -> x.getValue() ).collect(Collectors.toList()));
    }

    public static void main(String[] args) throws InterruptedException {

        final StarterArgs options = new StarterArgs();
        new JCommander(options).parse(args);

        ProcessStarter ps = Actors.AsActor(ProcessStarter.class);
        ps.init(options);

        new TCPNIOPublisher()
            .port(options.getPort())
            .facade(ps)
            .publish( act -> {
                System.out.println("Discon "+act);
            });

        // testing
        ProcessStarter remote = (ProcessStarter) new TCPConnectable(ProcessStarter.class,options.getHost(),options.getPort()).connect( (x,y) -> System.out.println("client disc "+x)).await();
        ProcessInfo bash = remote.startProcess("/tmp", Collections.emptyMap(), "bash", "-c", "xclock -digital").await();

        List<ProcessInfo> procs = remote.getProcesses().await();
        procs.forEach( proc -> System.out.println(proc));

//        System.out.println(bash);
        Thread.sleep(3000);


        Object await = remote.terminateProcess(bash.getId(), true, 15).await();
        System.out.println("term result "+await);

        //http://stackoverflow.com/questions/5740390/printing-my-macs-serial-number-in-java-using-unix-commands/5740673#5740673
        //http://stackoverflow.com/questions/1980671/executing-untokenized-command-line-from-java/1980921#1980921
    }

}