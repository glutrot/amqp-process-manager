# AMQP Process Manager

## What

AMQP Process Manager works as a simple **bridge to implement basic RPC over AMQP** by feeding messages received from AMQP message queues to any program via **stdin** and returning output generated on **stdout** to the caller using a simple JSON-based protocol. As it spawns **full processes on OS level**, it can also provide a **basic watchdog** which tries to terminate processes that did not finish (and did not send heartbeats) after a customizable timeout. The manager itself requires Java 8 and utilizes [Apache Camel](http://camel.apache.org/) to interface with AMQP but workers can be written in any language thanks to **OS level decoupling** without having to care about AMQP at all.

Current version requires [RabbitMQ](https://www.rabbitmq.com/) to be used as an AMQP server.

Released under **MIT license**.


## Why yet another AMQP client?

There surely is no shortage of [client-libraries](https://www.rabbitmq.com/devtools.html) to interface with AMQP message queues but there are a few downsides when you want to connect with an AMQP server:

* you need to find and read into a library that suits your programming language/environment as well as your and other participating developers' tastes from the vast selection of possible projects (quite many looking unmaintained and some even deprecated)
* the best-suited AMQP library for the job may not be available for the programming language you need
* if you are working with existing code, e.g. you want to make an existing application accessible via your website using AMQP and/or spanning server boundaries, you may have to rewrite your existing code so it fits the AMQP client library you chose
* while interfacing with AMQP is no problem, reliability may be an issue - few libraries appear to handle disconnects from AMQP servers well and you may have to implement workarounds for those cases or at least be very careful about how to implement your client which results in a lot of avoidable code if it was already handled by the AMQP library itself
* you may want to limit process runtime which would require further modifications to your original code and may not be possible to implement reliably in your programming language without decoupling it from the AMQP client library on an OS level
* as you figure "someone must have written that already" and search for it you end up at complex programs requiring lots of dependencies or specific environments which you may still have to write a wrapper for and which you may not have time for

When you run into these problems, you will eventually end up implementing a wrapper/bridge similar to this one yourself. At least that's what happened to us.


## Interfacing your program to the bridge

If your program has been written in Python, you may want to use our [Python implementation](https://github.com/glutrot/amqp-process-manager-lib-python).

Interfaces to other programming languages can easily be implemented; see [docs/PROTOCOL.md](docs/PROTOCOL.md) for details.


## Requirements / supported platforms

### Runtime

The only tested operating system so far is **Linux** but other UNIX-style operating systems should work as well (currently untested). You will only need Java SE 8 (JRE should be sufficient, but only JDK is tested so far).

You should use [RabbitMQ](https://www.rabbitmq.com/) as we currently use the specialized RabbitMQ component of Apache Camel, so other AMQP servers may not work yet (untested but should be easy to extend).

#### Support for other operating systems

Running AMQP Process Manager on other systems, e.g. Windows, may not work for two reasons:

* assumptions about Linux-style file system access
* different handling of OS processes may cause watchdog or process handling in general to fail

However, it should not be a hard challenge to introduce compatibility (if it doesn't already work, just needs testing first).

### Compile-time

As we currently do not supply pre-compiled binary packages you will need a **Java 8 SE development environment** to compile the source code. We require just two packages:

* [Java SE 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/index-jsp-138363.html)
* [Apache Maven](https://maven.apache.org/)

You may *compile* on any system you like (Linux, OS X, Windows, ... should not matter).


## How to run our bridge

### Single run during development

- clone this repository and compile the bridge using [Maven](https://maven.apache.org/) by running `mvn clean compile assembly:assembly` (sorry, no binary distribution yet)
- copy the default config file from [docs/config.example.json](docs/config.example.json)
- edit the config file, see [docs/CONFIGURATION.md](docs/CONFIGURATION.md) for an explanation of all possible options
- run the bridge using `java -jar target/amqpprocessmanager-0.1-SNAPSHOT-jar-with-dependencies.jar` (file name may differ based on current version)
  * the bridge expects the config file to be available from `~/.amqpprocessmanager.json` by default (`~` is the current user's home directory)
  * you may pass a different path as first argument: `java -jar target/amqpprocessmanager-0.1-SNAPSHOT-jar-with-dependencies.jar /my/config/is/elsewhere`

### Long-time deployment as a daemon

- clone & compile as shown above (`mvn clean compile assembly:assembly`)
- create some config and logging directories (such as `/etc/amqpprocessmanager` and `/var/log/amqpprocessmanager`)
- put your config file in the config directory (obvious...) and also create a logging configuration such as [docs/logging.example.properties](docs/logging.example.properties)
- depending on your system, you need an init script or service definition to start AMQP Process Manager on boot
  * an OpenRC init script for [Gentoo](https://www.gentoo.org/) is provided as [docs/gentoo-init](docs/gentoo-init) (save as `/etc/init.d/amqpprocessmanager` and configure `PIDFILE`, `DAEMONUSER`, `CONFIG` and `LOG_CONFIG` in `/etc/conf.d/amqpprocessmanager`)


## Dependencies & Licenses

AMQP Process Manager itself is released under [MIT license](LICENSE.md). However, we rely on dependencies using other, supposedly compatible licenses. Direct dependencies are listed below. For a list of transitive dependencies, please run `mvn project-info-reports:dependencies` to compile an up-to-date overview (will be created as `target/site/dependencies.html`).

* [Apache Camel Core](http://camel.apache.org/), Apache License 2.0
* [Gson](https://github.com/google/gson), Apache License 2.0
* [JSON.simple](http://code.google.com/p/json-simple/), Apache License 2.0
