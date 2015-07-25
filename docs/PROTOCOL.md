# Protocol

Communication runs over **stdin and stdout** as **one JSON-encoded array per RPC message**, separated by a **new line** character sequence. No other output must be printed to stdout. Regular output should use stderr instead which can also be logged by the manager if configured to do so.

The **first element** of each RPC message JSON array is a **string identifying the message type**. Further elements are optional and depend on the individual message type.

The new line sequence depends on the Java runtime implementation and thus the operating system. Processes may use any common sequence such as LF, CRLF or CR. Manager always uses OS default.


## from manager to processes

Currently, only one type of RPC message has been defined:

### plain messages

**Type identifier:** `message`  
**Number of elements:** 2

Directly passes the RPC caller's **AMQP message body (element 2, JSON string)** to the application process. Special characters need to be properly encoded according to JSON standard.

**Example:**  
```["message","The full AMQP message body. Special characters are properly encoded by JSON when required: \"\r\nlike this"]```


## from processes to manager

### heartbeat

**Type identifier:** `heartbeat`  
**Number of elements:** 1

Resets the manager's watchdog timer thus extending the process runtime. Should only be used at safe places in processes. If, for example, a process loops infinitely but sends a heartbeat on each iteration, it may never get terminated by the watchdog, defeating its purpose. You should also keep in mind that RPC callers may give up waiting for a result at some point, so it doesn't make sense to extend process runtime past maximum wait time for whole communication path (submission to AMQP + forwarding between servers + time in queue + process execution + return path).

**Example:**  
`["heartbeat"]`

### result message

**Type identifier:** `result`  
**Number of elements:** 2

Responds to RPC caller with the **AMQP message body (element 2, JSON string)** in the same way the manager passes a direct plain message to a process.

**Example:**  
```["result", "test"]
["result", "{\"a\": [1, 2, 3], \"c\": null, \"b\": {\"b4\": \"b4value\", \"b1\": 1.0, \"b2\": 1.1, \"b3\": 5}, \"d\": true}"]```
