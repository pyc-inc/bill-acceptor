# Bill Acceptor RS-232 Library

Communicates with a bill acceptor using [Flow](https://github.com/jodersky/flow), jodersky's rs232 serial communication library for Akka.

----
## which bill acceptors are supported?
1. [Apex 7000 Series](http://pyramidacceptors.com/apex-7000/)
2. [ID-003 Protocol](ftp://67.205.101.207/Pripherals/BillAcceptors/JCM/ID003/ID-003%20Protocol%20Spec.pdf) *(coming soon)*

----
## usage

Instantiate the acceptor actor inside an Akka parent actor.
 
    import inc.pyc.bill.acceptor._, Events._, Commands._
    lazy val acceptor = context.actorOf(BillAcceptor.props(context.system), "BillAcceptor")

Example of sending bill acceptor a command.

    acceptor ! Listen

### commands

**Listen** : listen to serial port and poll bill acceptor  
**UnListen** : stop listening to serial port  
**Inhibit** : accept all bills  
**UnInhibit**: do not accept bills  
**Stack** : accept the bill in escrow  
**Return** : do not accept the bill in escrow  

### events

**Disconnected** : not connected to serial port  
**Ready** : bill acceptor is ready to be used after sending listen command  
**Inserted(bill: Currency#Value)** : bill was inserted and is waiting in escrow mode  
**Confirmed(bill: Currency#Value)**: bill is or will be stacked and cannot be returned; safe to give credit   

----
## configuration

By default, configuration is not needed, but below is an example that can be added to application.conf in case changes need to be made. 

    bill-acceptor {
      currency = "USD"
      driver = "inc.pyc.bill.acceptor.apex.Apex"
      currency = "USD"
      port = "/dev/ttyUSB0"
      baud = 9600
      parity = 2
      char-size = 7
      buffer-size = 10
      two-stop-bits = off
    }

**Note**: the only supported currency at the moment is USD.

----
## installation

- flow-native needs to be installed to use serial communication. [Read here for basic instructions](https://github.com/jodersky/flow#basic-usage).
- This depends on a separate library: "currency-lib". In the same directory you git cloned this project, run "git clone https://github.com/pyc-inc/currency-lib.git" 
