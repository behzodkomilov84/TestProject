package behzoddev.testproject.telegram.callback;

import org.springframework.stereotype.Component;

@Component
public class CallbackDataParser {

    public ParsedCallback parse(String data) {

        String[] parts = data.split("_");

        String type = parts[0];

        if ("assignment".equals(type)) {

            return new ParsedCallback(
                    type,
                    Long.parseLong(parts[1]),
                    null
            );
        }

        if ("answer".equals(type)) {

            return new ParsedCallback(
                    type,
                    Long.parseLong(parts[1]),
                    Long.parseLong(parts[2])
            );
        }

        throw new RuntimeException("Unknown callback: " + data);
    }

    public record ParsedCallback(
            String type,
            Long assignmentId,
            Long answerId
    ) {}
}