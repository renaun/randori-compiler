/***
 * Copyright 2013 Teoti Graphix, LLC.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * 
 * @author Michael Schmalle <mschmalle@teotigraphix.com>
 */

package randori.compiler.codegen.js;

import java.util.Collection;

import org.apache.flex.compiler.problems.ICompilerProblem;
import org.apache.flex.compiler.tree.as.IExpressionNode;
import org.apache.flex.compiler.tree.as.IFunctionNode;

/**
 * The {@link IRandoriEmitter} interface allows abstraction between the base
 * JavaScript and the randori specific source code production.
 * 
 * @author Michael Schmalle
 */
public interface IRandoriEmitter extends IJSEmitter
{
    public static final String ANON_DELEGATE_NAME = "$createAnonDelegate";

    public static final String STATIC_DELEGATE_NAME = "$createStaticDelegate";

    // TODO (mschmalle) this should be in IASEmitter
    Collection<ICompilerProblem> getProblems();

    // TODO (mschmalle) this should be in IASEmitter
    void emitParamters(IFunctionNode node);

    // TODO (mschmalle) this should be in IASEmitter
    void emitMethodScope(IFunctionNode node);

    /**
     * Will swap the current write buffer with a {@link StringBuilder}, walk the
     * node passed and then return the String produced.
     * <p>
     * Important to note that the String is not actually written to the out
     * buffer.
     * 
     * @param node The node to stringify.
     */
    String toNodeString(IExpressionNode node);

    /**
     * Returns the session model for the current session.
     */
    ISessionModel getModel();

}
