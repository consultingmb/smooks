package org.milyn.general;

import org.milyn.SmooksException;
import org.milyn.container.ExecutionContext;
import org.milyn.delivery.sax.SAXElement;
import org.milyn.delivery.sax.SAXVisitAfter;

import java.io.IOException;

/**
 * @author
 */
public class SAXVisitor implements SAXVisitAfter {

    public static boolean match = false;

    public void visitAfter(SAXElement saxElement, ExecutionContext executionContext) throws SmooksException, IOException {
        match = true;
    }
}
