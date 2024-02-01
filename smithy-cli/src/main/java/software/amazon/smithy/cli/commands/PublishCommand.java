package software.amazon.smithy.cli.commands;

import software.amazon.smithy.cli.Arguments;
import software.amazon.smithy.cli.Command;

public class PublishCommand implements Command {
    @Override
    public String getName() {
        return "publish";
    }

    @Override
    public String getSummary() {
        return "Publishes a packaged model to a local maven repo";
    }

    @Override
    public int execute(Arguments arguments, Env env) {
        return 0;
    }
}
