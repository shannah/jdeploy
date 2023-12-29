package ca.weblite.jdeploy.openai.di;

import ca.weblite.jdeploy.openai.OpenAiKit;
import ca.weblite.jdeploy.openai.chat.PromptHandler;
import ca.weblite.jdeploy.openai.factory.ChatMessageFactory;
import ca.weblite.jdeploy.openai.factory.ChatPromptRequestFactory;
import ca.weblite.jdeploy.openai.factory.ChatThreadFactory;
import ca.weblite.jdeploy.openai.internal.DefaultOpenAiKit;
import ca.weblite.jdeploy.openai.internal.factory.ChatMessageFactoryImpl;
import ca.weblite.jdeploy.openai.internal.factory.ChatPromptRequestFactoryImpl;
import ca.weblite.jdeploy.openai.internal.factory.ChatThreadFactoryImpl;
import ca.weblite.jdeploy.openai.internal.service.ChatPromptImpl;
import org.codejargon.feather.Provides;

public class OpenAiModule {

    @Provides
    public ChatMessageFactory chatMessageFactory(ChatMessageFactoryImpl impl) {
        return impl;
    }

    @Provides
    public ChatPromptRequestFactory chatPromptRequestFactory(ChatPromptRequestFactoryImpl impl) {
        return impl;
    }

    @Provides
    public ChatThreadFactory chatThreadFactory(ChatThreadFactoryImpl impl) {
        return impl;
    }

    @Provides
    public OpenAiKit openAiKit(DefaultOpenAiKit impl) {
        return impl;
    }

    @Provides
    public PromptHandler promptHandler(ChatPromptImpl impl) {
        return impl;
    }
}
