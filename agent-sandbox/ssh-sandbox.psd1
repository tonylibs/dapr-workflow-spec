@{
    Image = "dws-agent-sandbox:kubectl"
    EntryPoint = @("/usr/local/bin/sshd-start")
    Timeout = "none"
    Cpu = "2"
    Memory = "4Gi"
    SshHost = "127.0.0.1"
    SshPort = 22222
    BridgePort = 2222
    BridgeImage = "alpine/socat:latest"
    Network = "bridge"
    PrivateKey = ".ssh\dws_sandbox"
    PublicKey = ".ssh\dws_sandbox.pub"
}
