package com.example.cli;

import java.util.logging.Logger;
import java.util.logging.Level;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "edp-cli",
    mixinStandardHelpOptions = true,
    version = "1.0",
    description = "Example CLI using Picocli",
    subcommands = {SayHello.class, SayBye.class}
)
public class EdpCli implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(EdpCli.class.getName());

    public static void main(String[] args) {
        int exitCode = new CommandLine(new EdpCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        LOGGER.log(Level.INFO, "Use a subcommand like say-hello or say-bye.");
    }
}

@Command(name = "say-hello", description = "Say hello to someone")
class SayHello implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(SayHello.class.getName());

    @Option(names = {"--name"}, description = "Name to greet", required = true)
    String name;

    @Override
    public void run() {
        LOGGER.log(Level.INFO, "Hello, {0}!", name);
    }
}

@Command(name = "say-bye", description = "Say goodbye to someone")
class SayBye implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(SayBye.class.getName());

    @Option(names = {"--name"}, description = "Name to say bye to", required = true)
    String name;

    @Override
    public void run() {
        LOGGER.log(Level.INFO, "Bye, {0}!", name);
    }
}
