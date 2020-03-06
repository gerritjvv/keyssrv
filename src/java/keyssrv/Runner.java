package keyssrv;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.RT;

/**
 *  Run the keyssrv clojure code.
 *   this is done to avoid oat and speed up the slow build time.
 */
public class Runner {



    public static void main(String args[]) {
        IFn require = Clojure.var("clojure.core", "require");
        require.invoke(Clojure.read("keyssrv.core"));

        IFn core = Clojure.var("keyssrv.core", "-main");
        core.applyTo(RT.arrayToList(args));
    }
}
