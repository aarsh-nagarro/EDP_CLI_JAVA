package com.example.cli;

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

    public static void main(String[] args) {
        int exitCode = new CommandLine(new EdpCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        System.out.println("Use a subcommand like say-hello or say-bye.");
    }
}

@Command(name = "say-hello", description = "Say hello to someone")
class SayHello implements Runnable {

    @Option(names = {"--name"}, description = "Name to greet", required = true)
    String name;

    @Override
    public void run() {
        System.out.println("Hello, " + name + "!");
    }
}

@Command(name = "say-bye", description = "Say goodbye to someone")
class SayBye implements Runnable {

    @Option(names = {"--name"}, description = "Name to say bye to", required = true)
    String name;

    @Override
    public void run() {
        System.out.println("Bye, " + name + "!");
    }
}
