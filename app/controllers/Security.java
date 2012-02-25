package controllers;

public class Security extends Secure.Security
{
    static void onDisconnected()
    {
        CRUD.index();
    }
}
