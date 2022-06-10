package mqtt;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Subscribe {

    private static final Logger logger = LoggerFactory.getLogger(Subscribe.class);

    private AtomicInteger counter = new AtomicInteger(0);

    private CountDownLatch cdl = new CountDownLatch(1000);

    //private static String ip = "tcp://172.16.32.14:1886";
    //private static String ip = "tcp://139.199.189.214:1883";
    private static String ip = "tcp://39.102.113.135:1883";

    //    private static String ip = "tcp://localhost:1883";
    public static void main(String[] args) {
        Subscribe subscribe = new Subscribe();
        subscribe.start("1");
    }

    /**
     * 启动
     */
    private void start(String prefix) {

        for (int i = 10000; i < 11000; i++) {

            final String clientId = prefix + "-" + i;
            Runnable thread = new Runnable() {

                public void run() {
                    logger.info("clientId:" + clientId);
                    connect(clientId);
                }
            };

            new Thread(thread).start();
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                logger.error("sleep error!", ex);
            }
        }

        try {
            cdl.await();
        } catch (InterruptedException ex) {
            logger.error("established error!", ex);
        }
        logger.info("client all established !");

    }

    public void connect(String clientId) {

        String broker = ip;

        MemoryPersistence persistence = new MemoryPersistence();

        try {
            MqttClient mqttClient = new MqttClient(broker, clientId, persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setUserName("client");
            connOpts.setPassword("123456".toCharArray());
            connOpts.setCleanSession(false);
            connOpts.setKeepAliveInterval(60);

            AtomicInteger subcounter = new AtomicInteger(0);
            ClientCallback clientCallback = new ClientCallback();
            clientCallback.setCounter(counter);
            clientCallback.setSubcounter(subcounter);
            clientCallback.setClientid(clientId);
            clientCallback.setMqttClient(mqttClient);
            clientCallback.setConnectionOpt(connOpts);

            mqttClient.setCallback(clientCallback);

            connOpts.setWill(mqttClient.getTopic("wifi/lost"), clientId.getBytes(), 1, true);

            mqttClient.connect(connOpts);
            mqttClient.subscribe("wifi/log");
            mqttClient.subscribe("wifi/log/" + clientId);

            cdl.countDown();

        } catch (MqttException me) {
            logger.error("subscribe error!", me);
        }
    }
}
