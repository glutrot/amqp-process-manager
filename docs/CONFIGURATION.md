# Process Manager Configuration

By default, configuration is tried to be read from `~/.amqpprocessmanager.json`. However, other configuration file names/paths can be used but have to be specified as first argument on process manager launch.

The configuration is specified as one JSON object using the following structure. Please see [docs/config.example.json](docs/config.example.json) for a complete example of all options.

## amqpCommonSettings

These settings are being passed to Apache Camel's [RabbitMQ component](http://camel.apache.org/rabbitmq.html) and describe how the AMQP connection should be handled:

Key                        | Default     | Description                                          
-------------------------- | ----------- | ------------------------------------------------------------------------------------------
`hostname`                 | `localhost` | host name or IP address of AMQP server to connect to 
`port`                     | `5672`      | port of AMQP server to connect to
`username`                 | none        | user name to connect with
`password`                 | none        | password to connect with
`vhost`                    | none        | AMQP virtual host ("namespace")
`automaticRecoveryEnabled` | `true`      | attempt to recover from intermittent connection failures? (handled by underlying library)
`connectionTimeout`        | 30000       | timeout of AMQP connection (in milliseconds)
`requestedHeartbeat`       | 10          | how frequently to request connection heartbeat (in seconds)


## processes

`processes` are given in an array of JSON objects, each describing one process using the following structure:

Key                        | Default     | Description                                          
-------------------------- | ----------- | ------------------------------------------------------------------------------------------
`name`                     | none        | internal name for process (used for logging, not for execution)
`concurrentConsumers`      | 1           | maximum number of instances to be spawned
`logStdErr`                | `false`     | log all lines the process prints to stderr?
`execution`                | n/a         | describing how to spawn and maintain a process, see JSON object structure below
`amqp`                     | n/a         | describing how to communicate via AMQP, see JSON object structure below

### execution

Key                        | Default     | Description                                          
-------------------------- | ----------- | ----------------------------------------------------------------------------------------------------------------------
`workDir`                  | none        | working directory to spawn process in
`executable`               | none        | path to process executable as seen from working directory (can, of course, also be an executable script)
`args`                     | empty       | a JSON array of arguments which should be passed on execution
`env`                      | empty       | a JSON object describing any supplemental environment variables, see below for details
`watchdogTimeout`          | 30          | process timeout (in seconds); watchdog will attempt to kill the process if it hasn't sent a heartbeat for at least this period of time
`watchdogCheckInterval`    | 200         | how often to check for process timeouts (in milliseconds)
`allowWritableExecutable`  | `false`     | sanity check usually requires executables not to be writable by the user who executes them; use this flag to override that check (not recommended except for locally controlled development!)

Processes will inherit the manager's environment variables by default. `env` allows to define additional variables but can also be used to override or supplement existing variables. To supplement an existing variable (a common use case would be appending to a `PATH` variable), you can use the marker `%%%ORIGINAL_VALUE%%%` which will be substituted by the original variable.

Example of appending to an existing variable:  
```'env': {
    'PYTHONPATH': '/where/ever:%%%ORIGINAL_VALUE%%%'
}```

### amqp

Key                        | Default     | Description                                          
-------------------------- | ----------- | ----------------------------------------------------------------------------------------------------------------------
`exchange`                 | empty       | name of exchange bind queue to
`exchangeType`             | `direct`    | exchange type (e.g. `direct` or `topic`)
`routingKey`               | none        | the queue's routing key (used for filtering messages, set to `null` if not applicable)
`queue`                    | none        | name of queue to read from

Remember that both consumer and producer should setup exchanges (and queues) upon connection, so you need to use the same configuration on your RPC callers. You may get a connection error on either end when using conflicting configuration.

In case you are not sure what to configure here, reading RabbitMQ's excellent [tutorial](http://www.rabbitmq.com/getstarted.html) (especially the chapter about [routing](http://www.rabbitmq.com/tutorials/tutorial-four-python.html)) may help.
