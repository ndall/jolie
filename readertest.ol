from console import Console
from runtime import Runtime 

service main{
    embed Console as console
    embed Runtime as runtime

    init{
        looping = true
    }

    main{
        while(looping){
            myReadLine@console("in")(line)
            if(line == "BUFFERED READER EOF ENCOUNTERED"){
                looping = false
            }else{
                println@console(line)()
            }
        }
    }
}