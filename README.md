# Java Instrumentation Tool

## Background

As a software security analyst, I regularly have to deal with the need to analyse Java-based applications, often in
a fat-client scenario. When it comes to instrumentation of the application, so far this was often done by de- and
recompiling the parts of the application to inspect some interesting-looking function calls, log packets before
they were wrapped in an encryption layer, etc.

I recently learned about the existence of the [Java Instrumentation API](https://docs.oracle.com/javase/7/docs/api/java/lang/instrument/Instrumentation.html) through
an excellent [blog post](https://www.baeldung.com/java-instrumentation) by [Adrian Precub](https://twitter.com/adrianprecub) on
[baeldung.com](https://www.baeldung.com). While I was able to whip up some example code that successfully modified Java
(byte-)code, I wanted it to be a bit more user-friendly - that's how this tool was born.

## Usage

### Hook Specification

The Java Instrumentation Tool can be used to create a Java Agent JAR file, which can be loaded either statically at
application start-up (with the `-javaagent` parameter to `java`) or dynamically by attaching to a running Java process.

In order to specify the modification of existing methods, we need to write a `hook.txt` file, which defines the modifications
in a CSV-like format. Let's look at an example:

```
$ cat hooks.txt
sun.security.ssl.X509TrustManagerImpl,checkServerTrusted,java.security.cert.X509Certificate[];java.lang.String;java.net.Socket,insertBefore,trustmanager.java
```

The first entry is the class that is to be modified, in this case `sun.security.ssl.X509TrustManagerImpl`. The second
entry is the method that should be modified (`checkServerTrusted`), while the third entry is the parameter types of this
method (`java.security.cert.X509Certificate[];java.lang.String;java.net.Socket`). Note that these are separated by `;`,
since `,` is already used. The fourth entry is the location where our hook code will be executed, either `insertBefore`
-- at the beginning of the method before any other code -- or `insertAfter` -- before any return statement in the method.
The last entry defined the file which contains the source code of our hook, this is supposed to live in a `hooks/` subdirectory
below the `hooks.txt` path. Let's look at `trustmanager.java`:

```
$ cat hooks/trustmanager.java
System.out.println("checkServerTrusted()");
return;
```

So this code intentionally skips the original code present in `checkServerTrusted` by adding a `return` statement before
it, which basically turns of any certificate validation. See how this might be useful?

### Building an agent.jar

In order to build an `agent.jar` file which contains this modification, we run the following command:

```
$ java -jar java-instrumentation-tool.jar hooks.txt
```

### Running the agent

This produces an `agent.jar` file which we can now either use at application start-up or dynamically with an already
running Java process.

Let's look at a very simple example application that just waits for five seconds and then tries to fetch https://self-signed.badssl.com/:

```java
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStreamReader;

public class Example {
    public static void main(String[] args) throws Exception {
        Thread.sleep(5000);
        URL url = new URL("https://self-signed.badssl.com/");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            System.out.println(inputLine);
        }
    }
}
```

When we compile and run it, we get an error due to the fact that the certificate chain validation fails:

```
javac Example.java
java Example
Exception in thread "main" javax.net.ssl.SSLHandshakeException: sun.security.validator.ValidatorException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
```

As expected. Let's see what happens if we now rerun the code with our `agent.jar`:

```
java -javaagent:agent.jar Example
[Agent] transforming sun.security.ssl.X509TrustManagerImpl.checkServerTrusted
[Agent] ClassTransformer constructor, sun.security.ssl.X509TrustManagerImpl, null
[Agent] Transforming class sun.security.ssl.X509TrustManagerImpl, method checkServerTrusted, param types java.security.cert.X509Certificate[];java.lang.String;java.net.Socket
[Agent] adding code before checkServerTrusted
checkServerTrusted()
<!DOCTYPE html>
<html>
[...]
```

We get some instructive output from the agent, including the `checkServerTrusted()` from inside our hook, and we can see
that the HTTPS connection now magically succeeds, because we overwrote the corresponding trust manager code!

Alternatively, we can also start the code and attach to it while it is running:

```
java Example & java -jar agent.jar $!
[1] 27509
[Agent] transforming sun.security.ssl.X509TrustManagerImpl.checkServerTrusted
[Agent] ClassTransformer constructor, sun.security.ssl.X509TrustManagerImpl, null
[Agent] Transforming class sun.security.ssl.X509TrustManagerImpl, method checkServerTrusted, param types java.security.cert.X509Certificate[];java.lang.String;java.net.Socket
[Agent] adding code before checkServerTrusted
checkServerTrusted()
<!DOCTYPE html>
<html>
[...]
```
