![NATS](src/main/javadoc/images/large-logo.png)

# NATS - Android Client

A [Android](http://www.android.com) client for the [NATS messaging system](https://nats.io).

[![License Apache 2](https://img.shields.io/badge/License-Apache2-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.spoton/nats-android/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.spoton/nats-android)
[![Javadoc](http://javadoc.io/badge/com.spoton/nats-android.svg?branch=master)](http://javadoc.io/doc/com.spoton/nats-android?branch=master)

This is version 2.4.6 of the Android API 21 port to the [java-nats](https://github.com/nats-io/java-nats) library. 

It has no external dependencies.  I replaced some JDK classes not available in Android API 21 with implementations based
on available JDK classes. 


## A Note on Versions

The NATS server renamed itself from gnatsd to nats-server around 2.4.6. This and other files try to use the new names, but some underlying code may change over several versions. If you are building yourself, please keep an eye out for issues and report them.

This is version 2.4.6 of the Android port to the java-nats library. This version is a ground up rewrite of the original library. Part of the goal of this re-write was to address the excessive use of threads, we created a Dispatcher construct to allow applications to control thread creation more intentionally. This version also removes all non-JDK runtime dependencies.

The API is [simple to use](#listening-for-incoming-messages) and highly [performant](#Benchmarking).

Version 2+ uses a simplified versioning scheme. Any issues will be fixed in the incremental version number. As a major release, the major version has been updated to 2 to allow clients to limit there use of this new API. With the addition of drain() we updated to 2.1, NKey support moved us to 2.2.

Previous versions are still available in the repo.

### UTF-8 Subjects

The client protocol spec doesn't explicitly state the encoding on subjects. Some clients use ASCII and some use UTF-8 which matches ASCII for a-Z and 0-9. Until 2.1.2 the 2.0+ version of the Java client used ASCII for performance reasons. As of 2.1.2 you can choose to support UTF-8 subjects via the Options. Keep in mind that there is a small performance penalty for UTF-8 encoding and decoding in benchmarks, but depending on your application this cost may be negligible. Also, keep in mind that not all clients support UTF-8 and test accordingly.


## Installation

The nats-android client is provided in a single jar file, with a single external dependency for the encryption in NKey support. See [Building From Source](#building-from-source) for details on building the library.

### Downloading the Jar

You can download the latest jar at [https://search.maven.org/remotecontent?filepath=com/spoton/nats-android/2.4.2/nats-android-2.4.6.jar](https://search.maven.org/remotecontent?filepath=com/spoton/nats-android/2.4.6/nats-android-2.4.6.jar).


### Using Gradle

```groovy
implementation 'com.spoton:nats-android:2.4.6'
```

## Basic Usage

Sending and receiving with NATS is as simple as connecting to the nats-server and publishing or subscribing for messages. There is an example Android app provided in this repo as described in [examples.md](example/README.md).

### Connecting

There are four different ways to connect using the Java library:

1. Connect to a local server on the default port:

    ```java
    Connection nc = Nats.connect();
    ```

2. Connect to a server using a URL:

    ```java
    Connection nc = Nats.connect("nats://myhost:4222");
    ```

3. Connect to one or more servers with a custom configuration:

    ```java
    Options o = new Options.Builder().server("nats://serverone:4222").server("nats://servertwo:4222").maxReconnects(-1).build();
    Connection nc = Nats.connect(o);
    ```

    See the javadoc for a complete list of configuration options.

4. Connect asynchronously, this requires a callback to tell the application when the client is connected:

    ```java
     Options options = new Options.Builder().server(Options.DEFAULT_URL).connectionListener(handler).build();
     Nats.connectAsynchronously(options, true);
    ```

    This feature is experimental, please let us know if you like it.

### Publishing

Once connected, publishing is accomplished via one of three methods:

1. With a subject and message body:

    ```java
    nc.publish("subject", "hello world".getBytes(StandardCharsets.UTF_8));
    ```

2. With a subject and message body, as well as a subject for the receiver to reply to:

    ```java
    nc.publish("subject", "replyto", "hello world".getBytes(StandardCharsets.UTF_8));
    ```

3. As a request that expects a reply. This method uses a Future to allow the application code to wait for the response. Under the covers a request/reply pair is the same as a publish/subscribe only the library manages the subscription for you.

    ```java
    Future<Message> incoming = nc.request("subject", "hello world".getBytes(StandardCharsets.UTF_8));
    Message msg = incoming.get(500, TimeUnit.MILLISECONDS);
    String response = new String(msg.getData(), StandardCharsets.UTF_8);
    ```

All of these methods, as well as the incoming message code use byte arrays for maximum flexibility. Applications can
send JSON, Strings, YAML, Protocol Buffers, or any other format through NATS to applications written in a wide range of
languages.

### Listening for Incoming Messages

The Java NATS library provides two mechanisms to listen for messages, three if you include the request/reply discussed above.

1. Synchronous subscriptions where the application code manually asks for messages and blocks until they arrive. Each subscription is associated with a single subject, although that subject can be a wildcard.

    ```java
    Subscription sub = nc.subscribe("subject");
    Message msg = sub.nextMessage(Duration.ofMillis(500));

    String response = new String(msg.getData(), StandardCharsets.UTF_8);
    ```

2. A Dispatcher that will call application code in a background thread. Dispatchers can manage multiple subjects with a single thread and single callback.

    ```java
    Dispatcher d = nc.createDispatcher((msg) -> {
        String response = new String(msg.getData(), StandardCharsets.UTF_8);
        ...
    });

    d.subscribe("subject");
    ```

## Advanced Usage

### TLS

NATS supports TLS 1.2. The server can be configured to verify client certificates or not. Depending on this setting the client has several options.

You can include a truststore in your application assets as opposed to system truststore.  To initiate an SSL context for your NATS Connection

```java
        val truststore = KeyStore.getInstance("BKS")
        assetManager.open(KEYSTORE_FILE).use {
            truststore.load(it, KEYSTORE_PASSWORD.toCharArray())
        }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(truststore)
        val sc = SSLContext.getInstance("TLS")
        sc.init(null, tmf.trustManagers, java.security.SecureRandom())

        return Options.Builder()
                .server(NATS_SERVER)
                .userInfo(NATS_USER, NATS_PASSWORD)
                .sslContext(sc)
```

### Clusters & Reconnecting

The Java client will automatically reconnect if it loses its connection the nats-server. If given a single server, the client will keep trying that one. If given a list of servers, the client will rotate between them. When the nats servers are in a cluster, they will tell the client about the other servers, so that in the simplest case a client could connect to one server, learn about the cluster and reconnect to another server if its initial one goes down.

To tell the connection about multiple servers for the initial connection, use the `servers()` method on the options builder, or call `server()` multiple times.

```Java
String[] serverUrls = {"nats://serverOne:4222", "nats://serverTwo:4222"};
Options o = new Options.Builder().servers(serverUrls).build();
```

Reconnection behavior is controlled via a few options, see the javadoc for the Options.Builder class for specifics on reconnect limits, delays and buffers.


## Building From Source

The build depends on Gradle, and contains `gradlew` to simplify the process. After cloning, you can build the repository and run the tests with a single command:

```bash
> git clone https://github.com/SpotOnInc/nats-android.git
> cd java-nats
> ./gradlew build
```

This will place the class files in a new `build` folder. To just build the jar:

```bash
> ./gradlew jar
```

The jar will be placed in `build/libs`.

You can also build the java doc, and the samples jar using:

```bash
> ./gradlew javadoc
> ./gradlew exampleJar
```

The java doc is located in `build/docs` and the example jar is in `build/libs`. Finally, to run the tests with the coverage report:

```bash
> ./gradlew test jacocoTestReport
```

which will create a folder called `build/reports/jacoco` containing the file `index.html` you can open and use to browse the coverage. Keep in mind we have focused on library test coverage, not coverage for the examples.

Many of the tests run nats-server on a custom port. If nats-server is in your path they should just work, but in cases where it is not, or an IDE running tests has issues with the path you can specify the nats-server location with the environment variable `nats_-_server_path`.

## License

Unless otherwise noted, the NATS source files are distributed
under the Apache Version 2.0 license found in the LICENSE file.
