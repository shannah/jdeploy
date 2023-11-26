package ca.weblite.jdeploy.openai.internal.service;

import ca.weblite.jdeploy.openai.functions.GenerateProjectFunction;
import ca.weblite.jdeploy.openai.functions.GetClassSourceFunction;
import ca.weblite.jdeploy.openai.functions.GetProjectFunction;
import ca.weblite.jdeploy.openai.functions.PutClassSourceFunction;
import com.theokanning.openai.completion.chat.ChatFunction;
import com.theokanning.openai.completion.chat.ChatFunctionCall;
import com.theokanning.openai.service.FunctionExecutor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;

@Singleton
public class JDeployFunctionExecutor {
    private final FunctionExecutor functionExecutor;

    @Inject
    public JDeployFunctionExecutor(
            GenerateProjectFunction generateProjectFunction,
            GetProjectFunction getProjectFunction,
            GetClassSourceFunction getClassSourceFunction,
            PutClassSourceFunction putClassSourceFunction
    ) {
        this.functionExecutor = new FunctionExecutor(Arrays.asList(
                generateProjectFunction.asChatFunction(),
                getProjectFunction.toChatFunction(),
                getClassSourceFunction.toChatFunction(),
                putClassSourceFunction.toChatFunction()
        ));
    }

    public List<ChatFunction> getFunctions() {
        return functionExecutor.getFunctions();
    }

    public com.theokanning.openai.completion.chat.ChatMessage executeAndConvertToMessage(ChatFunctionCall call) {
        return functionExecutor.executeAndConvertToMessage(call);
    }
}
