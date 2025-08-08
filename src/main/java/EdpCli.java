package com.example.cli;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "edp-cli",
    mixinStandardHelpOptions = true,
    version = "edp-cli 1.0",
    description = "A CLI tool to greet and bid farewell.",
    subcommands = {
        EdpCli.SayHello.class,
        EdpCli.SayBye.class
    }
)
public class EdpCli implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new EdpCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "say-hello", description = "Greet someone with hello.")
    static class SayHello implements Runnable {

        @Option(names = {"--name"}, description = "Name of the person to greet", required = true)
        String name;

        @Override
        public void run() {
            System.out.printf("Hello, %s! 👋 %n", name);
        }
    }

    @Command(name = "say-bye", description = "Say goodbye to someone.")
    static class SayBye implements Runnable {

        @Option(names = {"--name"}, description = "Name of the person to bid farewell", required = true)
        String name;

        @Override
        public void run() {
            System.out.printf("Goodbye, %s! 👋 %n", name);
        }
    }
}
