public class FieldsAndLocals {
    private int a = 0;
    {
        int a = -1;
        new B(){
            public void run(){
                int b = <ref>a
            }
        }
    }
}

abstract class B implements Runnable {
    private int a = 0;
}

