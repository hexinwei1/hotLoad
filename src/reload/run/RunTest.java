package reload.run;

public class RunTest {
    public static void main(String[] args) throws InterruptedException {
        TransTest1 transSrc1 = new TransTest1();
        TransTest2 transSrc2 = new TransTest2();

        while (true) {
            transSrc1.print();
            transSrc2.print();
            Thread.sleep(5000);
        }
    }
}
