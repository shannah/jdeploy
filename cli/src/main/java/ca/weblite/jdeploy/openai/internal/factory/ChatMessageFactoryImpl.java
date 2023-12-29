package ca.weblite.jdeploy.openai.internal.factory;

public class ChatMessageFactoryImpl implements ca.weblite.jdeploy.openai.factory.ChatMessageFactory {

    public ca.weblite.jdeploy.openai.model.ChatMessage createChatMessage(
            ca.weblite.jdeploy.openai.model.ChatMessageRole role,
            String content,
            String name
    ) {
        return new ca.weblite.jdeploy.openai.internal.model.ChatMessageImpl(
                new com.theokanning.openai.completion.chat.ChatMessage(
                        role.value(),
                        content,
                        name
                )
        );
    }
}
