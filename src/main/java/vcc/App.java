package vcc;

/**
 * Hello world!
 *
 */
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.twill.api.AbstractTwillRunnable;
import org.apache.twill.api.ClassAcceptor;
import org.apache.twill.api.TwillController;
import org.apache.twill.api.TwillRunnerService;
import org.apache.twill.api.logging.PrinterLogHandler;
import org.apache.twill.yarn.YarnTwillRunnerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Hello World example using twill-yarn to run a TwillApplication over YARN.
 */
public class App {
    public static final Logger LOG = LoggerFactory.getLogger(App.class);

    /**
     * Hello World runnable that is provided to TwillRunnerService to be run.
     */
    private static class HelloWorldRunnable extends AbstractTwillRunnable {
        @Override
        public void run() {
            LOG.info("Hello World. My first distributed application.");
        }

        @Override
        public void stop() {
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Arguments format: <host:port of zookeeper server>");
            System.exit(1);
        }

        String zkStr = args[0];
        YarnConfiguration yarnConfiguration = new YarnConfiguration();
        yarnConfiguration.addResource(new Path(App.class.getClassLoader().getResource("yarn-site.xml").getPath()));
        yarnConfiguration.addResource(new Path(App.class.getClassLoader().getResource("core-site.xml").getPath()));
        final TwillRunnerService twillRunner = new YarnTwillRunnerService(yarnConfiguration, zkStr);
        twillRunner.start();

        String yarnClasspath =
                yarnConfiguration.get(YarnConfiguration.YARN_APPLICATION_CLASSPATH,
                        Joiner.on(",").join(YarnConfiguration.DEFAULT_YARN_APPLICATION_CLASSPATH));
        List<String> applicationClassPaths = Lists.newArrayList();
        Iterables.addAll(applicationClassPaths, Splitter.on(",").split(yarnClasspath));
        final TwillController controller = twillRunner.prepare(new HelloWorldRunnable())
                .addLogHandler(new PrinterLogHandler(new PrintWriter(System.out, true)))
                .withApplicationClassPaths(applicationClassPaths)
                .withBundlerClassAcceptor(new HadoopClassExcluder())
                .start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    Futures.getUnchecked(controller.terminate());
                } finally {
                    twillRunner.stop();
                }
            }
        });

        try {
            controller.awaitTerminated();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    static class HadoopClassExcluder extends ClassAcceptor {
        @Override
        public boolean accept(String className, URL classUrl, URL classPathUrl) {
            // exclude hadoop but not hbase package
            return !(className.startsWith("org.apache.hadoop") && !className.startsWith("org.apache.hadoop.hbase"));
        }
    }
}