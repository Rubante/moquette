package mqtt;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.RandomStringUtils;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PublisherMass {

    private static final Logger logger = LoggerFactory.getLogger(PublisherMass.class);

    private static AtomicInteger counter = new AtomicInteger(0);

    private static String ip = "tcp://172.16.32.14:1886";
    // private static String ip = "tcp://localhost:1884";

    private static int qos = 2;

    private static int minute = 1;

    public static void main(String[] args) {

        if (args.length >= 2) {
            qos = Integer.parseInt(args[1]);
        }

        if (args.length >= 3) {
            minute = Integer.parseInt(args[2]);
        }

        for (int i = 0; i < 1; i++) {
            Runnable thread = new Runnable() {

                public void run() {
                    connect(RandomStringUtils.randomAlphabetic(14));
                }
            };

            new Thread(thread).start();
        }
    }

    public static void connect(String clientId) {

        String broker = ip;

        MemoryPersistence persistence = new MemoryPersistence();

        try {
            final MqttClient mqttClient = new MqttClient(broker, clientId, persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();

            connOpts.setUserName("admin");
            connOpts.setPassword("123456".toCharArray());

            connOpts.setCleanSession(true);

            PublisherCallback callback = new PublisherCallback();
            callback.setQos(qos);
            callback.setMinute(minute);
            callback.setCounter(counter);
            mqttClient.setCallback(callback);
            mqttClient.connect(connOpts);

            callback.setMqttClient(mqttClient);

            callback.run();

        } catch (MqttException me) {
            logger.error("mqtt send error", me);
        }
    }
}
