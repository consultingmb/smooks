package example.groovy

import org.milyn.container.ExecutionContext
import org.milyn.delivery.sax.SAXVisitAfter

public class Test implements SAXVisitAfter {

    public void visitAfter(Element element, ExecutionContext executionContext) 
    {
        println ("In Test.groovy..." + k)
    }

}
