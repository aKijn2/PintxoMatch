# How to run

- To run just open the terminal and paste this in the same location:
```
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\start-dev-cloudflare.ps1 -TunnelTimeoutSeconds 120 -SkipDefaultResolverCheck
```