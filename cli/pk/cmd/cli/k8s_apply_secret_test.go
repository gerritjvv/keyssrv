package cli

import (
	"k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes/fake"
	"pk/swagger"
	"testing"
)

func TestK8sApplyEnvs(t *testing.T) {

	envs := []swagger.Env{
		{Lbl: "test3",
			Val: `
apiVersion: v1
kind: Secret
metadata:
  name: mysecret1
  namespace: default
type: Opaque
stringData:
  username: mysecretname
---
apiVersion: v1
kind: Secret
metadata:
  name: mysecret2
  namespace: default
type: Opaque
stringData:
  username: mysecretname
  key: |
    Vdfjkdfajkds
    eiqpewqoemm.,
    ---
    weqiwoeeo
		`},
		{Lbl: "test4",
			Val: `
apiVersion: v1
kind: Secret
metadata:
 name: mysecret3
 namespace: default
type: Opaque
stringData:
 username: mysecretname
`},
	}

	clientSet := fake.NewSimpleClientset()
	err := applyEnvs(clientSet, envs)

	if err != nil {
		t.Errorf("%s\n", err)
	}

	getOptions := v1.GetOptions{}

	_, err = clientSet.CoreV1().Secrets("default").Get("mysecret1", getOptions)
	if err != nil {
		t.Errorf("%s\n", err)
	}
	_, err = clientSet.CoreV1().Secrets("default").Get("mysecret2", getOptions)
	if err != nil {
		t.Errorf("%s\n", err)
	}
	_, err = clientSet.CoreV1().Secrets("default").Get("mysecret3", getOptions)
	if err != nil {
		t.Errorf("%s\n", err)
	}

}

//func applyEnvYamlDoc(clientset *kubernetes.Clientset, envLbl string , yamlDoc string) error{
func TestK8sApplySecret(t *testing.T) {

	var yamlDoc = `
apiVersion: v1
kind: Secret
metadata:
  name: mysecret
  namespace: default
type: Opaque
stringData:
  username: mysecretname
`

	clientSet := fake.NewSimpleClientset()
	err := applyEnvYamlDoc(clientSet, "mylbl", yamlDoc)

	if err != nil {
		t.Errorf("%s\n", err)
	}

	//_, err = clientSet.CoreV1().Secrets("default").Get("mysecret", nil)

	//if err != nil {
	//	t.Errorf("%s\n", err)
	//}

}
