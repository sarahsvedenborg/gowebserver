package main

import (
    "io"
    "fmt"
    "log"
    "net/http"
    "strconv"
)

func hello(w http.ResponseWriter, r *http.Request) {
    io.WriteString(w, "Hello world!")
}

func to_roman(n int)  string {
  m := map[int]string {1:"I", 2:"II", 3:"III", 4:"IV", 5:"V", 6:"VI", 7:"VII"}
  val, ok := m[n]
  if ok {
    return val
  } else {
    return "CDIV: Roman not Found"
  }
}


type romanGenerator int
func (n romanGenerator) ServeHTTP(w http.ResponseWriter, r *http.Request) {
    ascii_num := r.URL.Path[7:]
    i, err := strconv.Atoi(ascii_num)
    if err != nil {
        log.Print(err)
    }
    fmt.Fprintf(w, "Here's your number: %s\n", to_roman(i))
}



func main() {
    h := http.NewServeMux()

    h.Handle("/roman/", romanGenerator(1))
    h.HandleFunc("/", hello)

    err := http.ListenAndServe(":8000", h)
    log.Fatal(err)
}
