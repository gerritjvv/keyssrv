package log

import (
	"fmt"
	apex "github.com/apex/log"
	"github.com/apex/log/handlers/text"
	//_ "github.com/apex/log/handlers/text"
	"os"
	"pk/db/util"
)

var ctx *apex.Entry

//"debug":   DebugLevel,
//	"info":    InfoLevel,
//	"warn":    WarnLevel,
//	"warning": WarnLevel,
//	"error":   ErrorLevel,
//	"fatal":   FatalLevel,

func init() {

	ctx = apex.WithFields(apex.Fields{})
	apex.SetHandler(text.New(os.Stderr))

	apex.SetLevelFromString(util.OrStr(
		os.Getenv("LOG_LEVEL"),
		os.Getenv("LOGLEVEL"),
		"info"))

}

func Debugf(msg string, v ...interface{}) {
	ctx.Debugf(msg, v...)
}

func Warnf(msg string, v ...interface{}) {
	ctx.Warnf(msg, v...)
}

func Errorf(msg string, v ...interface{}) {
	ctx.Errorf(msg, v...)
}

func Infof(msg string, v ...interface{}) {
	fmt.Fprintf(os.Stderr, msg + "\n", v...)
}
