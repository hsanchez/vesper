package edu.ucsc.refactor.cli;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import edu.ucsc.refactor.*;
import edu.ucsc.refactor.spi.CommitRequest;
import edu.ucsc.refactor.spi.CommitStatus;
import edu.ucsc.refactor.util.Notes;
import edu.ucsc.refactor.util.StringUtil;
import io.airlift.airline.*;
import io.airlift.airline.Cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Queue;

import static io.airlift.airline.OptionType.COMMAND;

/**
 * A very basic CLI Interpreter.
 *
 * @author hsanchez@cs.ucsc.edu (Huascar A. Sanchez)
 */
public class Interpreter {

    final Cli<VesperCommand> parser;
    final Environment        environment;
    final StringReader       reader;

    static final String VERSION = "Vesper v0.0.0";

    public Interpreter(){
        Cli.CliBuilder<VesperCommand> builder = Cli.<VesperCommand>builder("vesper")
                .withDescription("the nice CLI for Vesper")
                .withDefaultCommand(HelpCommand.class)
                .withCommand(HelpCommand.class)
                .withCommand(ResetCommand.class)
                .withCommand(InspectCommand.class)
                .withCommand(ReplCommand.class)
                .withCommand(ConfigCommand.class)
                .withCommand(AddCommand.class)
                .withCommand(OriginShow.class)
                .withCommand(RemoveCommand.class)
                .withCommand(PublishCommand.class)
                .withCommand(FormatCommand.class);

        builder.withGroup("rename")
                .withDescription("Manage set of renaming commands")
                .withDefaultCommand(RenameClass.class)
                .withCommand(RenameClass.class)
                .withCommand(RenameMethod.class)
                .withCommand(RenameParameter.class)
                .withCommand(RenameField.class);

        builder.withGroup("chomp")
                .withDescription("munch noises (irrelevant code) from SOURCE")
                .withDefaultCommand(ChompClass.class)
                .withCommand(ChompClass.class)
                .withCommand(ChompMethod.class)
                .withCommand(ChompParam.class)
                .withCommand(ChompField.class);

        builder.withGroup("chop")
                .withDescription("cut specific code sections from SOURCE")
                .withDefaultCommand(ChopClass.class)
                .withCommand(ChopClass.class);

        builder.withGroup("notes")
                .withDescription("Manage set of notes about SOURCE")
                .withDefaultCommand(NotesShow.class)
                .withCommand(NotesShow.class)
                .withCommand(NoteAdd.class);

        reader      = new StringReader();
        parser      = builder.build();
        environment = new Environment();
    }

    public Result evaluateAndReturn(String statement) throws RuntimeException {
        final Iterable<String> args = reader.process(statement);
        if(Iterables.isEmpty(args)){
            return Result.nothing();
        }

        return eval(Iterables.toArray(args, String.class));
    }

    public Result eval(String... args) throws RuntimeException {
        return parser.parse(args).call(environment);
    }

    public void clears() {
        environment.clears();
    }

    public Environment getEnvironment(){
        return environment;
    }


    @Command(name = "reset", description = "Reset modified source to its original state")
    public static class ResetCommand extends VesperCommand {
        @Arguments(description = "Reset command parameters")
        public List<String> patterns;

        @Override public Result execute(Environment environment) throws RuntimeException {
            ensureValidState(environment);

            Preconditions.checkArgument((patterns == null) || (patterns.size() == 1));

            if(patterns != null && patterns.size() == 1){
                final Source indexed = environment.reset(patterns.get(0));
                return Result.sourcePackage(indexed);
            } else {
                environment.reset();
                return Result.sourcePackage(environment.getOrigin()); // show the new origin
            }
        }

        @Override public String toString() {
            return Objects.toStringHelper("ResetCommand")
                    .add("params", patterns)
                    .toString();
        }
    }


    static abstract class RenameVesperCommand extends VesperCommand {
        @Arguments(description = "Rename operation parameters")
        public List<String> patterns;

        @Override public Result execute(Environment environment) throws RuntimeException {
            Preconditions.checkNotNull(environment);
            Preconditions.checkNotNull(patterns);
            Preconditions.checkArgument(!patterns.isEmpty());
            Preconditions.checkArgument(patterns.size() == 2);


            // (1,2)-> head
            // newName-> tail
            final String head = patterns.get(0).replace("[", "").replace("]", "");
            final String tail = patterns.get(1);

            final SourceSelection selection = createSelection(environment, head);

            final ChangeRequest request   = createChangeRequest(selection, tail);
            final CommitRequest applied   = commitChange(environment, request);

            return createResultPackage(
                    applied,
                    "unable to commit '"
                            + request.getCauseOfChange().getName().getKey()
                            +  "' change."
            );
        }

        protected abstract ChangeRequest createChangeRequest(SourceSelection selection, String newName);

        @Override public String toString() {
            return Objects.toStringHelper(getClass())
                    .add("params", patterns)
                    .toString();
        }
    }


    static abstract class ChompVesperCommand extends VesperCommand {
        @Arguments(description = "Chomp operation parameters")
        public List<String> patterns;

        @Override public Result execute(Environment environment) throws RuntimeException {
            ensureValidState(environment);

            Preconditions.checkNotNull(patterns);
            Preconditions.checkArgument(!patterns.isEmpty());
            Preconditions.checkArgument(patterns.size() == 1);


            // [1,2]-> head
            final String head = patterns.get(0).replace("[", "").replace("]", "");

            final SourceSelection selection = createSelection(environment, head);

            final ChangeRequest request   = createChangeRequest(selection);
            final CommitRequest applied   = commitChange(environment, request);

            return createResultPackage(applied, "unable to commit 'chomp' change");
        }

        protected abstract ChangeRequest createChangeRequest(SourceSelection selection);

        @Override public String toString() {
            return Objects.toStringHelper(getClass())
                    .add("params", patterns)
                    .toString();
        }
    }

    @Command(name = "help", description = "Display help information about airship")
    public static class HelpCommand extends VesperCommand {
        @Inject
        public Help help;

        @Override public Result execute(Environment environment) throws RuntimeException {
            help.call();
            return Environment.unit(); // nothing to show
        }

        @Override public String toString() {
            return Objects.toStringHelper("HelpCommand")
                    .add("help", help)
                    .toString();
        }
    }


    @Command(name = "add", description = "Add file contents to the index")
    public static class AddCommand extends VesperCommand {
        @Option(type = COMMAND, name = {"-f", "--file"}, description = "Add a file")
        public boolean file = false;

        @Arguments(description = "Patterns of files to be added")
        public List<String> patterns;

        @Override public Result execute(Environment environment) throws RuntimeException {
            Preconditions.checkNotNull(environment);
            Preconditions.checkNotNull(patterns);
            Preconditions.checkArgument(!patterns.isEmpty(), "add... was given no arguments");

            if(file){
                final String path = Preconditions.checkNotNull(Iterables.get(patterns, 0, null));
                final String name = StringUtil.extractName(path);
                final String cont = SourceFileReader.readContent(path);

                return compareAndSet(environment, name + ".java", cont);

            } else {
                final String head    = Preconditions.checkNotNull(Iterables.get(patterns, 0, null));
                final String tail    = Preconditions.checkNotNull(Iterables.get(patterns, 1, null));

                final boolean headWithExt = "java".equals(Files.getFileExtension(head));
                final boolean tailWithExt = "java".equals(Files.getFileExtension(tail));

                final String name       = headWithExt && !tailWithExt ? head : tail;
                final String content    = !headWithExt && tailWithExt ? head : tail;

                return compareAndSet(environment, name, content);
            }
        }

        private Result compareAndSet(Environment environment, String name, String content){
            if(environment.containsOrigin()){
                // ask to continue
                if (!ask("Are you sure you would like to REPLACE the existing SOURCE?", false)) {
                    return Result.nothing();
                }
            }

            environment.setOrigin(
                    new Source(
                            name,
                            content
                    )
            );


            return Result.sourcePackage(environment.getOrigin());
        }
    }

    @Command(name = "remove", description = "Remove file contents to the index")
    public static class RemoveCommand extends VesperCommand {

        @Arguments(description = "Command to execute on the existing Source")
        public String name;

        @Override public Result execute(Environment environment) throws RuntimeException {
            Preconditions.checkNotNull(environment);
            Preconditions.checkNotNull(name);

            if(environment.containsOrigin()){
                if(environment.getOrigin().getName().equals(name) || "origin".equals(name)){
                    // ask to continue
                    if (!ask("Are you sure you would like to REMOVE the existing SOURCE?", false)) {
                        return Result.nothing();
                    }

                    environment.setOrigin(null);

                    return Result.infoPackage(name + " was removed!");
                }
            }

            return Result.infoPackage("There is nothing to remove!");
        }

        @Override public String toString() {
            return Objects.toStringHelper("RemoveCommand")
                    .add("target", name)
                    .toString();
        }
    }


    @Command(name = "config", description = "Configure access to a remote repository")
    public static class ConfigCommand extends VesperCommand {

        static final int USERNAME       = 0;
        static final int USERNAME_VALUE = 1;
        static final int PASSWORD       = 2;
        static final int PASSWORD_VALUE = 3;


        @Arguments(description = "Credentials to a remote repository")
        public List<String> credentials;

        @Override public Result execute(Environment environment) throws RuntimeException {
            Preconditions.checkNotNull(environment);
            Preconditions.checkNotNull(credentials);
            Preconditions.checkState(!credentials.isEmpty());

            Preconditions.checkArgument(credentials.size() == 4);

            Preconditions.checkArgument("username".equals(credentials.get(USERNAME)));
            Preconditions.checkArgument("password".equals(credentials.get(PASSWORD)));


            final String username  = credentials.get(USERNAME_VALUE);
            final String password  = credentials.get(PASSWORD_VALUE);

            final boolean allSet  = environment.setCredential(username, password);

            if(allSet){
                if(globalOptions.verbose){
                    return Result.infoPackage("Ok, credentials have been set!\n");
                }
            }

            return Result.nothing();
        }

        @Override public String toString() {
            return Objects.toStringHelper("ConfigCommand")
                    .add("username", credentials.get(USERNAME_VALUE))
                    .add("password", credentials.get(PASSWORD_VALUE))
                    .toString();
        }
    }


    @Command(name = "repl", description = "Interactive Vesper")
    public static class ReplCommand extends VesperCommand {

        @Option(name = "-c", description = "Enter remote credentials")
        public boolean config = false;

        @Arguments(description = "Interactive Vesper parameters")
        public List<String> patterns;

        @Override public Result execute(Environment environment) throws RuntimeException {
            Preconditions.checkNotNull(environment);

            try {
                Credential credential = null;
                if(config){

                    Preconditions.checkNotNull(patterns);
                    Preconditions.checkArgument(!patterns.isEmpty());
                    Preconditions.checkArgument(patterns.size() == 4, "Unknown parameters");

                    Preconditions.checkArgument("username".equals(patterns.get(0)));
                    Preconditions.checkArgument("password".equals(patterns.get(2)));


                    final String username  = patterns.get(1);
                    final String password  = patterns.get(3);

                    credential = new Credential(username, password);

                }

                if(runRepl(credential, environment)){
                    // todo(Huascar) think of a better strategy of how to return things...
                    System.out.println("quitting " + VERSION + " Good bye!");
                    return Result.sourcePackage(environment.getOrigin());
                }

            } catch (Throwable ex){
                throw new RuntimeException(ex);
            }

            return Environment.unit();
        }

        private static boolean runRepl(Credential credential, Environment global) throws IOException {
            System.out.println();
            System.out.println(VERSION);
            System.out.println("-----------");
            System.out.println("Type 'q' and press Enter to quit.");


            InputStreamReader converter = new InputStreamReader(System.in);
            BufferedReader in = new BufferedReader(converter);

            Interpreter repl = new Interpreter();

            if(credential != null){
                repl.getEnvironment().setCredential(credential);
            }


            Result result = null;

            while (true) {
                System.out.print("vesper> ");

                String line = in.readLine();

                if (line.equals("q")) {
                    // ask to continue
                    if (!AskQuestion.ask("Are you sure you would like to quit " + VERSION, false)) {
                        continue;
                    } else {
                        // bubble up changes done in REPL mode to the global
                        // environment (scope), and then clear the local
                        // environment.
                        global.clears();
                        global.setOrigin(repl.getEnvironment().getOrigin());
                        repl.clears();
                        return true; // exiting ivr
                    }
                }

                if (line.equals("help")) {
                    repl.eval("help");
                    continue;
                }

                if(line.equals("repl")){
                    repl.print(VERSION + ", yeah! that's me.\n");
                    continue; // no need to call it again
                }


                if(line.equals("log")){
                    if(result != null){
                        if(result.isCommitRequest()){
                            repl.printResult(result.getCommitRequest().more());
                        } else if(result.isSource()){
                            repl.printResult(result.getSource().getContents());
                        }
                    }

                    continue;
                }

                try {
                    result = repl.evaluateAndReturn(line);
                } catch (ParseException ex){
                    repl.printError("Unknown command");
                    continue;
                }

                if(result.isError()){
                    repl.printError(result.getErrorMessage());
                } else if (result.isInfo()){
                    if(!result.getInfo().isEmpty()){
                        repl.print("= " + result.getInfo());
                    }
                } else if (result.isIssuesList()){
                    final List<Issue> issues = result.getIssuesList();
                    for(int i = 0; i < issues.size(); i++){
                        repl.print(String.valueOf(i + 1) + ". ");
                        repl.print(issues.get(i).getName().getKey() + ".");
                        repl.print("\n");
                    }
                }  else if(result.isSource()){
                    repl.printResult(result.getSource().getContents());
                }
            }

        }



        @Override public String toString() {
            return Objects.toStringHelper("ReplCommand")
                    .add("config", config)
                    .toString();
        }
    }

    @Command(name = "inspect", description = "Shows the issues in the Source that should be fixed")
    public static class InspectCommand extends VesperCommand {

        @Arguments(description = "Source to inspect")
        public String name;

        @Override public Result execute(Environment environment) throws RuntimeException {
            ensureValidState(environment);

            final boolean inspectOrigin = Strings.isNullOrEmpty(name)
                    || "java".equals(Files.getFileExtension(name));


            List<Issue> issues = Lists.newArrayList();
            if(environment.containsOrigin() && inspectOrigin){
                issues = environment.getRefactorer().getIssues(environment.getOrigin());
                if(issues.isEmpty()){
                    return Result.infoPackage("No issues to show.\n");
                }
            }

            return Result.issuesListPackage(issues);   // maybe we should return only the text of each issue

        }

        @Override public String toString() {
            return Objects.toStringHelper("InspectCommand")
                    .toString();
        }
    }


    @Command(name = "show", description = "Shows all recorded notes")
    public static class NotesShow extends VesperCommand {
        @Override public Result execute(Environment environment) throws RuntimeException {
            ensureValidState(environment);

            final Notes notes   = environment.getOrigin().getNotes();
            final StringBuilder text    = new StringBuilder();

            for(Note each : notes){
                text.append(each.getContent()).append("\n");
            }

            return Result.infoPackage(text.toString());
        }

        @Override public String toString() {
            return Objects.toStringHelper("NotesShow")
                    .toString();
        }
    }

    @Command(name = "add", description = "Adds a note")
    public static class NoteAdd extends VesperCommand {
        @Arguments(description = "Note to add")
        public String note;

        @Override public Result execute(Environment environment) throws RuntimeException {
            ensureValidState(environment);

            final String noteToAdd = Preconditions.checkNotNull(note);

            environment.getOrigin().addNote(
                    // todo(Huascar) add SourceRange (1, 2) or [1,2]
                    new Note(/*[1,2]*/noteToAdd)
            );

            return Environment.unit();
        }
    }


    @Command(name = "class", description = "Renames a class or interface found in the SOURCE")
    public static class RenameClass extends RenameVesperCommand {
        @Override protected ChangeRequest createChangeRequest(SourceSelection selection, String newName) {
            return ChangeRequest.renameClassOrInterface(selection, newName);
        }
    }


    @Command(name = "method", description = "Renames a method found in the SOURCE")
    public static class RenameMethod extends RenameVesperCommand {
        @Override protected ChangeRequest createChangeRequest(SourceSelection selection, String newName) {
            return ChangeRequest.renameMethod(selection, newName);
        }
    }


    @Command(name = "param", description = "Renames a parameter found in the SOURCE's method")
    public static class RenameParameter extends RenameVesperCommand {
        @Override protected ChangeRequest createChangeRequest(SourceSelection selection, String newName) {
            return ChangeRequest.renameParameter(selection, newName);
        }
    }

    @Command(name = "field", description = "Renames a field found in the SOURCE's class")
    public static class RenameField extends RenameVesperCommand {
        @Override protected ChangeRequest createChangeRequest(SourceSelection selection, String newName) {
            return ChangeRequest.renameField(selection, newName);
        }
    }


    @Command(name = "publish", description = "Publish all recorded commits")
    public static class PublishCommand extends VesperCommand {
        @Override public Result execute(Environment environment) throws RuntimeException {
            ensureValidState(environment);

            final Queue<CommitRequest> requests = environment.getRequests();
            final Queue<CommitRequest> skipped  = Lists.newLinkedList();

            final StringBuilder details = new StringBuilder();
            while(!requests.isEmpty()){
                final CommitRequest request = requests.remove();
                final CommitStatus status  = environment.getRefactorer().publish(request).getStatus();

                if(status.isAborted()){
                    skipped.add(request);
                } else {
                    details.append(status.more());
                    if(!requests.isEmpty()){
                        details.append("\n");
                    }
                }
            }

            if(requests.isEmpty() && skipped.isEmpty()){
                return Result.infoPackage(
                        "\nGreat!, all commits have been published. See details:\n"
                                + details.toString()
                );
            } else {
                while(!skipped.isEmpty()){
                    environment.put(skipped.remove());
                }

                return Result.infoPackage(
                        "A total of "
                                + environment.getRequests().size()
                                + " commits were not published. Tried again later."
                );
            }
        }

        @Override public String toString() {
            return Objects.toStringHelper("PublishCommand")
                    .toString();
        }
    }

    @Command(name = "format", description = "Formats the tracked SOURCE")
    public static class FormatCommand extends VesperCommand {
        @Override public Result execute(Environment environment) throws RuntimeException {
            ensureValidState(environment);


            final ChangeRequest request = ChangeRequest.reformatSource(environment.getOrigin());
            final CommitRequest applied = commitChange(environment, request);

            return createResultPackage(applied, "unable to commit 'format code' change");
        }

        @Override public String toString() {
            return Objects.toStringHelper("FormatCommand")
                    .toString();
        }
    }


    @Command(name = "class", description = "Chop a class from the recorded SOURCE")
    public static class ChopClass extends VesperCommand {
        @Arguments(description = "Chop class parameters")
        public List<String> patterns;

        @Override public Result execute(Environment environment) throws RuntimeException {
            ensureValidState(environment);

            Preconditions.checkNotNull(patterns);
            Preconditions.checkArgument(!patterns.isEmpty());
            Preconditions.checkArgument(patterns.size() == 2);

            return Result.infoPackage(
                    "Supported chomping strategies:\n\t\t"
                            + "1. Chop class\n\t\t"
                            + "2. Chop method\n\t\t"
                            + "3. Chop parameter\n\t\t"
                            + "4. Chop field\n"
            );
        }

        @Override public String toString() {
            return Objects.toStringHelper("ChopClass")
                    .toString();
        }
    }

    @Command(name = "class", description = "Remove unused inner class from SOURCE")
    public static class ChompClass extends ChompVesperCommand {
        @Override protected ChangeRequest createChangeRequest(SourceSelection selection) {
            return ChangeRequest.deleteClass(selection);
        }
    }


    @Command(name = "method", description = "Remove unused method from SOURCE")
    public static class ChompMethod extends ChompVesperCommand {
        @Override protected ChangeRequest createChangeRequest(SourceSelection selection) {
            return ChangeRequest.deleteMethod(selection);
        }
    }

    @Command(name = "param", description = "Remove unused param from SOURCE's method")
    public static class ChompParam extends ChompVesperCommand {
        @Override protected ChangeRequest createChangeRequest(SourceSelection selection) {
            return ChangeRequest.deleteParameter(selection);
        }
    }


    @Command(name = "field", description = "Remove unused param from SOURCE's method")
    public static class ChompField extends ChompVesperCommand {
        @Override protected ChangeRequest createChangeRequest(SourceSelection selection) {
            return ChangeRequest.deleteField(selection);
        }
    }

    @Command(name = "show", description = "Shows the current SOURCE")
    public static class OriginShow extends VesperCommand {
        @Override public Result execute(Environment environment) throws RuntimeException {
            ensureValidState(environment);

            if(!environment.containsOrigin()){
                return Result.nothing();
            }

            return Result.sourcePackage(environment.getOrigin());
        }

        @Override public String toString() {
            return Objects.toStringHelper("OriginShow")
                    .toString();
        }
    }



    public void print(String text) {
        System.out.print(text);
    }


    public void printResult(String result) {
        System.out.print("= ");
        System.out.println(result);
    }

    public void printError(String message) {
        System.out.println("! " + message);
    }


}