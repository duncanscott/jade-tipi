'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useSession } from 'next-auth/react';

const baseNavLinks = [
    { href: '/', label: 'Home' },
    { href: '/list', label: 'Documents' },
    { href: '/document/create', label: 'Create' },
];

const adminNavLinks = [
    { href: '/admin/groups', label: 'Groups' },
];

export default function Header() {
    const pathname = usePathname();
    const { data: session } = useSession();
    const isAdmin = session?.isAdmin === true;
    const navLinks = isAdmin ? [...baseNavLinks, ...adminNavLinks] : baseNavLinks;

    return (
        <header className="header">
            <h1>Jade Tipi</h1>
            <nav className="nav">
                {navLinks.map(({ href, label }) => {
                    const isActive = pathname === href || pathname?.startsWith(`${href}/`);
                    return (
                        <Link
                            key={href}
                            href={href}
                            style={isActive ? { fontWeight: 'bold', textDecoration: 'underline' } : {}}
                        >
                            {label}
                        </Link>
                    );
                })}
            </nav>
        </header>
    );
}
