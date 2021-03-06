package kontraktor.sometest;

import org.nustaq.kontraktor.*;
import org.nustaq.kontraktor.remoting.encoding.Coding;
import org.nustaq.kontraktor.remoting.encoding.SerializerType;
import org.nustaq.kontraktor.remoting.http.undertow.HttpPublisher;
import org.nustaq.kontraktor.remoting.http.undertow.WebSocketPublisher;
import org.nustaq.serialization.coders.Unknown;

import java.util.Arrays;

/**
 * Created by ruedi on 05.07.17. maps to testclient.js
 */
public class TestActeur extends Actor<TestActeur> {

    private String name;

    public void setName(String name) {
        this.name = name;
    }

    public IPromise<String> getName() {
        return new Promise<>(name);
    }

    int count = 0;
    public IPromise<TestActeur> createAnotherOne(String name) {
        TestActeur testActeur = AsActor(TestActeur.class,getScheduler());
        testActeur.setName(name+" "+count++);
        return new Promise<>(testActeur);
    }

    public void plain( String arg, int arg1 ) {
        System.out.println("plain "+arg+" "+arg1);
    }

    public IPromise<String> plainPromise(String arg, int arg1 ) {
        String x = "plainPromise " + arg + " " + arg1;
        System.out.println(x);
        return new Promise<>(x);
    }

    public IPromise<String> plainCallback(String arg, int arg1, Callback cb ) {
        String x = "plainCallback " + arg + " " + arg1;
        System.out.println(x);
        cb.pipe(arg).pipe(arg1).finish();
        return new Promise<>(x);
    }

    public IPromise<TestPojo> plainPojo(TestPojo in) {
        return new Promise(in);
    }

    public void plainUnknown(Unknown in) {
        System.out.println("unknown"+in);
    }

    public void simpleTypes( String[] arr, int[] iarr ) {
        System.out.println(Arrays.toString(arr));
        System.out.println(Arrays.toString(iarr));
    }

    ///////////////////////////////// startup /////////////////////////////////////////////////////////

    public static void mainHttp(String[] args) {
        new HttpPublisher(Actors.AsActor(TestActeur.class),"localhost", "/test", 8888)
            .coding(new Coding(SerializerType.JsonNoRef))
            .publish( x -> System.out.println("DISCONNECTED:"+x));
    }

    public static void mainWS(String[] args) {
        new WebSocketPublisher(Actors.AsActor(TestActeur.class),"localhost", "/test", 8888)
            .coding(new Coding(SerializerType.JsonNoRef))
            .publish( x -> System.out.println("DISCONNECTED:"+x));
    }

    public static void main(String[] args) {
        mainHttp(args);
    }

}
