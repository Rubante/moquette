package mqtt;

public class Startup {

    public static void main(String[] args) {

        if (args == null || args.length < 1) {
            System.err.println("参数错误");
        } else if (args[0].equals("server")) {
            Publisher.main(args);
        } else if (args[0].equals("mass")) {
            PublisherMass.main(args);
        } else if (args[0].equals("client")) {
            Subscribe.main(args);
        }
    }

}
