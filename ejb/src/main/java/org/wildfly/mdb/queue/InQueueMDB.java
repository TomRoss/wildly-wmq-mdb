package org.wildfly.mdb.queue;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.ejb.MessageDrivenContext;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.ObjectMessage;
import jakarta.jms.Queue;
import jakarta.jms.QueueConnectionFactory;
import jakarta.jms.QueueConnection;
import jakarta.jms.QueueSender;
import jakarta.jms.QueueSession;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.ejb3.annotation.ResourceAdapter;
import org.jboss.logging.Logger;

/**
 * Created by tomr on 20/12/13.
 */

@MessageDriven(name = "InQueueMDB", activationConfig = {
        @ActivationConfigProperty(propertyName = "maxPoolDepth", propertyValue="3"),
        @ActivationConfigProperty(propertyName = "maxMessages", propertyValue="1"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue"),
        @ActivationConfigProperty(propertyName = "useJNDI", propertyValue = "false"),
        @ActivationConfigProperty(propertyName = "hostName", propertyValue = "rex"),
        @ActivationConfigProperty(propertyName = "port", propertyValue = "1414"),
        @ActivationConfigProperty(propertyName = "channel", propertyValue = "DEV.APP.SVRCONN"),
        @ActivationConfigProperty(propertyName = "queueManager", propertyValue = "${wmq.queue.manager}"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "${example.in.queue}"),
        @ActivationConfigProperty(propertyName = "userName",propertyValue = "${wmq.user}"),
        @ActivationConfigProperty(propertyName = "password",propertyValue = "${wmq.password}"),
        @ActivationConfigProperty(propertyName = "transportType", propertyValue ="${wmq.transport.type}") })

@ResourceAdapter(value = "wmq.jakarta.rar")
public class InQueueMDB implements MessageListener {
    private static final Logger log = Logger.getLogger(InQueueMDB.class);
    private static AtomicInteger mdbCnt = new AtomicInteger(0);
    private static String hostName= null;
    private int msgCnt = 0;
    private int mdbID = 0;

    @Resource
    private MessageDrivenContext ctx;
    @Resource( name = "java:jboss/jms/wmq/outQueue")
    private Queue outQueue;
    @Resource( name = "java:jboss/jms/wmq/MQCF")
    private QueueConnectionFactory  qcf;

    private TextMessage txtMsg = null;
    private ObjectMessage objMsg = null;
    private QueueConnection qConnection = null;
    private QueueSession qSession = null;
    private QueueSender qSender = null;


    private int totalMsgCnt = 0;
    private boolean throwException = false;

    public InQueueMDB(){

        mdbCnt.incrementAndGet();
        mdbID = mdbCnt.get();
    }

    public void onMessage(Message message){

        try {

            log.info("MDB[" + mdbID + "] Got message - '" + message);

            if ( message instanceof TextMessage){

                msgCnt++;

                txtMsg = (TextMessage) message;

                log.debug("MDB[" + mdbID + "] Got text message '" + txtMsg.getText() + "'.");

                if (qcf == null) {

                    log.info("MDB[" + mdbID + "] no QCF found.");

                    return;
                }
                qConnection = qcf.createQueueConnection();
                qSession = qConnection.createQueueSession(true, Session.SESSION_TRANSACTED);

                qSender = qSession.createSender(outQueue);

                qSender.send(message);

                if (log.isDebugEnabled()){

                    log.debug("MDB[" + mdbID + "] Sent message '" + message + "' to destination '" + outQueue.toString() + "'.");
                }

                throwException = txtMsg.getBooleanProperty("ThrowException");

                if (throwException){

                    log.info("MDB[" + mdbID + "] This message asked to throw RuntimeException.");

                    throw new RuntimeException("This is a dummy exception thrown from onMessage() method.");
                }

                log.info("MDB[" + mdbID + "] The unique value = " + txtMsg.getStringProperty("UniqueValue") + ".");


            } else if (message instanceof ObjectMessage){

                objMsg = (ObjectMessage) message;

                log.info("MDB[" + mdbID + "] Got object message of class '" + objMsg.getObject().getClass().getName() + "'.");

            }  else {

                log.warn("MDB[" + mdbID + "] Unknown message type.");

            }

        } catch (JMSException jmsEx){

            ctx.setRollbackOnly();

            log.error("MDB[" + mdbID + "] JMS Error",jmsEx);

        }  finally {

            try {

                if (qSender != null) {

                    qSession.close();

                    log.debug("MDB[" + mdbID + "] qSender closed.");
                }

                if (qSession != null){

                    qSession.close();

                    log.debug("MDB[" + mdbID + "] qSession closed.");
                }

                if (qConnection != null){

                    qConnection.close();

                    log.debug("MDB[" + mdbID + "] qConnection closed.");
                }

            } catch ( JMSException jmsEx){

                log.warn("MDB[" + mdbID + "] Problem while cleaning jms resources.",jmsEx);

            }
        }
    }

    @PostConstruct
    public void init(){
        log.info("MDB[" + mdbID + "] created.");

        if (hostName == null){

            try {

                hostName = InetAddress.getLocalHost().getHostName();

            } catch (UnknownHostException e) {

                log.warn("MDB[" + mdbID + "] Problem obtaining host name, setting hostName to 'unknown'.");

                hostName = "unknown";
            }
        }
    }

    @PreDestroy
    public void cleanUp(){
        log.info("MDB[" + mdbID + "] Processed " + msgCnt + " messages.");
        log.info("MDB[" + mdbID + "] Closing.");
    }

}
